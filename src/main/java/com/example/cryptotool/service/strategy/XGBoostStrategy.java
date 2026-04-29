package com.example.cryptotool.service.strategy;

import org.springframework.stereotype.Component;

import com.example.cryptotool.infrastructure.MlPredictionClient;
import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;

import lombok.RequiredArgsConstructor;

/**
 * 【戦略700】XGBoost単体戦略
 * リスクリワード比を1.5:1.0に固定し、数学的にプラス収支を目指す完成形
 */
@Component
@RequiredArgsConstructor
public class XGBoostStrategy implements TradingStrategy {

    private final MlPredictionClient mlClient;
    
    // 💡 閾値を0.60に引き上げ、確実性を向上
    private static final double THRESHOLD_BUY = 0.60;
    private static final double THRESHOLD_SELL = 0.40;

    @Override public int getStrategyId() { return 700; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double[] features = buildFeatureVector(key, current, dataStore);
        double upProbability = mlClient.getPredictionProbability(features);
        double atr = dataStore.getATR(key, 14, 0);
        
        if (upProbability >= THRESHOLD_BUY) {
            // 💡 利確はATRの1.5倍、損切りは1.0倍。勝率50%以上で資産が増える設計
            double tp = current.getClose() + (atr * 1.5);
            double sl = current.getClose() - (atr * 1.0);
            return new SignalDecision(SignalType.BUY, 701, "AI上昇確信(" + String.format("%.2f", upProbability) + ")", tp, sl);
        }

        if (upProbability <= THRESHOLD_SELL) {
            double tp = current.getClose() - (atr * 1.5);
            double sl = current.getClose() + (atr * 1.0);
            return new SignalDecision(SignalType.SELL, 702, "AI下落確信(" + String.format("%.2f", upProbability) + ")", tp, sl);
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        // 基本は指値(TP/SL)で決済するため、追加ロジックはなし（ドテン時のみエンジンが処理）
        return null;
    }

    public double[] buildFeatureVector(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double rsi = dataStore.getRSI(key, 14, 0);
        double stdDev = dataStore.getStdDev(key, 20, 0);
        double maDev = dataStore.getMaDeviationRate(key, 20, 0);
        double ma5 = dataStore.getPastMA(key, 5, 0);
        double ma25 = dataStore.getPastMA(key, 25, 0);
        double f4_macd = (ma25 != 0) ? ((ma5 - ma25) / ma25) * 100 : 0; 
        
        double open = current.getOpen();
        double close = current.getClose();
        double f5_body = (close != 0) ? (Math.abs(close - open) / close) * 100 : 0; 
        double f6_upper = (close != 0) ? ((current.getHigh() - Math.max(open, close)) / close) * 100 : 0; 
        double f7_lower = (close != 0) ? ((Math.min(open, close) - current.getLow()) / close) * 100 : 0; 
        double stdDevPct = (close != 0) ? (stdDev / close) * 100 : 0;
        
        double prevVol = 0;
        java.util.List<CandleData> history = dataStore.getHistoryMap().get(key);
        if (history != null && !history.isEmpty()) {
            prevVol = history.get(history.size() - 1).getVolume();
        }
        double volRatio = (prevVol != 0) ? (current.getVolume() / prevVol) * 100 : 100;
        
        // 💡 時間足の秒数から分数を算出（record構造に対応）
        double tfValue = key.timeFrame().getSeconds() / 60.0;

        // 💡 NaN対策
        if (Double.isNaN(rsi)) rsi = 50.0;
        if (Double.isNaN(stdDevPct)) stdDevPct = 0.0;
        if (Double.isNaN(maDev)) maDev = 0.0;
        if (Double.isNaN(f4_macd)) f4_macd = 0.0;
        if (Double.isInfinite(volRatio) || Double.isNaN(volRatio)) volRatio = 100.0;
        
        return new double[]{ rsi, stdDevPct, maDev, f4_macd, f5_body, f6_upper, f7_lower, volRatio, tfValue };
    }
}