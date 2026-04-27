package com.example.cryptotool.service.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;

/**
 * 【戦略500系】EMAパーフェクトオーダー（通常版 ＋ 緩和版 対応）
 */
@Component
public class EmaPerfectOrderStrategy implements TradingStrategy {

    private final Map<MarketKey, Double> trailingStopMap = new ConcurrentHashMap<>();

    @Override
    public int getStrategyId() {
        return 500;
    }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        // --- 指標の取得 ---
        double ema5 = dataStore.getPastEMA(key, 5, 0);
        double ema20 = dataStore.getPastEMA(key, 20, 0);
        double ema50 = dataStore.getPastEMA(key, 50, 0);
        double ema200 = dataStore.getPastEMA(key, 200, 0);
        
        double ema5Prev = dataStore.getPastEMA(key, 5, 1);
        double ema20Prev = dataStore.getPastEMA(key, 20, 1);
        double ema50Prev = dataStore.getPastEMA(key, 50, 1);
        double ema200Prev = dataStore.getPastEMA(key, 200, 1);
        
        double[] adx = dataStore.getADX(key, 14, 0);
        double atr = dataStore.getATR(key, 14, 0);
        double prevClose = dataStore.getPastCandleClose(key, 1);

        // ==========================================
        // 501: 買い (LONG) 判定
        // ==========================================
        boolean isBreakoutUp = prevClose <= ema5Prev && current.getClose() > ema5;

        // 【1. 通常版の判定】(厳格: 全ての線が対象, ADX>=15)
        boolean isPerfectOrderUp_Normal = (ema5 > ema20) && (ema20 > ema50) && (ema50 > ema200);
        boolean isAllUp_Normal = (ema5 > ema5Prev) && (ema20 > ema20Prev) && (ema50 > ema50Prev) && (ema200 > ema200Prev);
        
        if (isPerfectOrderUp_Normal && isAllUp_Normal && adx[0] >= 15 && isBreakoutUp) {
            double sl = current.getClose() - 1.5 * atr;
            trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.BUY, 501, "【戦略501:通常版】EMA PO(買い)", 0.0, sl);
        }

        // 【2. 緩和版の判定】(通常版を満たさなかった場合のみ、ゆるい条件で判定)
        // 緩和内容: 200EMAを除外し、短期〜中期のトレンドのみで判断。ADXも10に下げる。
        boolean isPerfectOrderUp_Relaxed = (ema5 > ema20) && (ema20 > ema50);
        boolean isAllUp_Relaxed = (ema5 > ema5Prev) && (ema20 > ema20Prev);
        
        if (isPerfectOrderUp_Relaxed && isAllUp_Relaxed && adx[0] >= 10 && isBreakoutUp) {
            double sl = current.getClose() - 1.5 * atr;
            trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.BUY, 501, "【戦略501:緩和版】短期EMA PO(買い)", 0.0, sl);
        }

        // ==========================================
        // 502: 売り (SHORT) 判定
        // ==========================================
        boolean isBreakoutDown = prevClose >= ema5Prev && current.getClose() < ema5;

        // 【1. 通常版の判定】
        boolean isPerfectOrderDown_Normal = (ema5 < ema20) && (ema20 < ema50) && (ema50 < ema200);
        boolean isAllDown_Normal = (ema5 < ema5Prev) && (ema20 < ema20Prev) && (ema50 < ema50Prev) && (ema200 < ema200Prev);
        
        if (isPerfectOrderDown_Normal && isAllDown_Normal && adx[0] >= 15 && isBreakoutDown) {
            double sl = current.getClose() + 1.5 * atr;
            trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.SELL, 502, "【戦略502:通常版】EMA PO(売り)", 0.0, sl);
        }

        // 【2. 緩和版の判定】
        boolean isPerfectOrderDown_Relaxed = (ema5 < ema20) && (ema20 < ema50);
        boolean isAllDown_Relaxed = (ema5 < ema5Prev) && (ema20 < ema20Prev);
        
        if (isPerfectOrderDown_Relaxed && isAllDown_Relaxed && adx[0] >= 10 && isBreakoutDown) {
            double sl = current.getClose() + 1.5 * atr;
            trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.SELL, 502, "【戦略502:緩和版】短期EMA PO(売り)", 0.0, sl);
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, 
                                    String currentPosition, double entryPrice, long entryTime) {
        
        double atr = dataStore.getATR(key, 14, 0);
        double[] macd = dataStore.getMACD(key, 0); 
        double[] macdPrev = dataStore.getMACD(key, 1);
        
        if ("LONG".equals(currentPosition)) {
            double currentSl = trailingStopMap.getOrDefault(key, 0.0);
            double newSl = current.getClose() - 1.5 * atr;
            if (newSl > currentSl || currentSl == 0.0) { trailingStopMap.put(key, newSl); currentSl = newSl; }
            
            if (current.getClose() <= currentSl) {
                trailingStopMap.remove(key); return new SignalDecision(SignalType.SELL, 501, "【戦略501利確/損切】トレイリング到達");
            }
            if (macdPrev[0] >= macdPrev[1] && macd[0] < macd[1]) {
                trailingStopMap.remove(key); return new SignalDecision(SignalType.SELL, 501, "【戦略501利確】MACDデッドクロス");
            }

        } else if ("SHORT".equals(currentPosition)) {
            double currentSl = trailingStopMap.getOrDefault(key, Double.MAX_VALUE);
            if (currentSl == 0.0) currentSl = Double.MAX_VALUE;
            double newSl = current.getClose() + 1.5 * atr;
            if (newSl < currentSl) { trailingStopMap.put(key, newSl); currentSl = newSl; }
            
            if (current.getClose() >= currentSl) {
                trailingStopMap.remove(key); return new SignalDecision(SignalType.BUY, 502, "【戦略502利確/損切】トレイリング到達");
            }
            if (macdPrev[0] <= macdPrev[1] && macd[0] > macd[1]) {
                trailingStopMap.remove(key); return new SignalDecision(SignalType.BUY, 502, "【戦略502利確】MACDゴールデンクロス");
            }
        }
        return null;
    }
}