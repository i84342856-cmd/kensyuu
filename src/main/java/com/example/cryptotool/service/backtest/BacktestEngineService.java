package com.example.cryptotool.service.backtest;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cryptotool.infrastructure.CryptoCompareClient;
import com.example.cryptotool.model.BacktestResult;
import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;
import com.example.cryptotool.service.strategy.TradingStrategy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BacktestEngineService {

    private final List<TradingStrategy> strategies;
    private final CryptoCompareClient cryptoCompareClient;

    public BacktestEngineService(List<TradingStrategy> strategies, CryptoCompareClient cryptoCompareClient) {
        this.strategies = strategies;
        this.cryptoCompareClient = cryptoCompareClient;
    }

    public BacktestResult runBacktest(Symbol symbol, TimeFrame tf, int targetStrategyId, int lookbackCandles) {
        TradingStrategy targetStrategy = strategies.stream()
                .filter(s -> s.getStrategyId() == targetStrategyId || (targetStrategyId / 100 * 100) == s.getStrategyId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("指定された戦略IDが見つかりません: " + targetStrategyId));

        List<CandleData> history = cryptoCompareClient.getHistoricalCandles(symbol, tf, lookbackCandles);
        
        // ★修正: 制限を200本から「最低50本」に大きく緩和
        if (history == null || history.size() < 50) {
            throw new IllegalStateException("バックテストに必要な過去データが取得できませんでした(取得数: " + (history == null ? 0 : history.size()) + ")");
        }

        MarketDataStore mockDataStore = new MarketDataStore(cryptoCompareClient);
        MarketKey key = new MarketKey(symbol, tf);

        double initialBalance = 1000000.0; 
        double currentBalance = initialBalance;
        double peakBalance = initialBalance; 
        double maxDrawdown = 0.0;
        
        String currentPosition = "NONE";
        double entryPrice = 0.0;
        long entryTime = 0L;
        
        int winCount = 0;
        int loseCount = 0;

        log.info("🚀 バックテスト開始: 戦略={}, 通貨={}, 足={}, データ数={}", targetStrategy.getClass().getSimpleName(), symbol, tf, history.size());

        // ★修正: 助走期間を取得データサイズの半分（最大200）に動的設定
        int warmupCandles = Math.min(200, history.size() / 2);

        for (int i = 0; i < history.size(); i++) {
            CandleData currentCandle = history.get(i);
            
            mockDataStore.addCandleForBacktest(key, currentCandle);
            
            // 助走期間はスキップ
            if (i < warmupCandles) continue; 

            if (!"NONE".equals(currentPosition)) {
                SignalDecision exitSignal = targetStrategy.checkExit(key, currentCandle, mockDataStore, currentPosition, entryPrice, entryTime);
                
                if (exitSignal != null) {
                    double tradeSize = 400000.0 / currentCandle.getClose(); 
                    
                    double pnl = ("LONG".equals(currentPosition)) 
                            ? (currentCandle.getClose() - entryPrice) * tradeSize 
                            : (entryPrice - currentCandle.getClose()) * tradeSize;
                    
                    currentBalance += pnl;
                    if (pnl > 0) winCount++; else loseCount++;
                    
                    if (currentBalance > peakBalance) peakBalance = currentBalance;
                    double drawdown = peakBalance - currentBalance;
                    if (drawdown > maxDrawdown) maxDrawdown = drawdown;

                    currentPosition = "NONE"; 
                }
            }

            if ("NONE".equals(currentPosition)) {
                SignalDecision entrySignal = targetStrategy.checkEntry(key, currentCandle, mockDataStore);
                
                if (entrySignal != null) {
                    currentPosition = entrySignal.type() == SignalType.BUY ? "LONG" : "SHORT";
                    entryPrice = currentCandle.getClose();
                    entryTime = currentCandle.getTime();
                }
            }
        }

        int totalTrades = winCount + loseCount;
        double winRate = totalTrades > 0 ? (double) Math.round(((double) winCount / totalTrades * 100) * 10) / 10 : 0.0;
        double totalProfit = Math.round(currentBalance - initialBalance);

        log.info("🏁 バックテスト完了: 純利益={}円, 勝率={}%, 最大ドローダウン={}円", totalProfit, winRate, Math.round(maxDrawdown));

        return new BacktestResult(
                targetStrategy.getClass().getSimpleName(),
                totalTrades, winCount, loseCount, winRate, totalProfit, Math.round(maxDrawdown), Math.round(currentBalance)
        );
    }
}