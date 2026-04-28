package com.example.cryptotool.service.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.SwingPoint;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;

/**
 * 【高度分析版】トライアングル・ブレイクアウト戦略
 * 線形回帰によるトレンドライン判定と多次元スコアリングを実装。
 */
@Component
public class TriangleBreakoutStrategy implements TradingStrategy {
    private final Map<MarketKey, Double> targetPriceMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Double> stopLossMap = new ConcurrentHashMap<>();

    private static final double ENTRY_THRESHOLD = 80.0; // 合計スコアがこれを超えたらエントリー

    @Override 
    public int getStrategyId() { return 300; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        List<SwingPoint> swings = dataStore.getRecentSwings(key, 5);
        if (swings == null || swings.size() < 5) return null;

        // スイングポイントを高値・安値のリストに分離
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        for (SwingPoint sp : swings) {
            if (sp.isHigh()) highs.add(sp.price());
            else lows.add(sp.price());
        }

        double sma50 = dataStore.getPastMA(key, 50, 0);
        double sma200 = dataStore.getPastMA(key, 200, 0);

        // --- 301: アセンディング・トライアングル（買い）の判定 ---
        if (swings.get(4).isHigh() && swings.get(2).isHigh() && swings.get(0).isHigh()) {
            double score = calculateAscendingScore(highs, lows, current, sma50, sma200, key, dataStore);

            if (score >= ENTRY_THRESHOLD) {
                double avgHigh = highs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double recentLow = lows.get(lows.size() - 1);
                
                double tp = current.getClose() + (avgHigh - recentLow);
                double sl = recentLow * 0.99;
                
                targetPriceMap.put(key, tp); 
                stopLossMap.put(key, sl);
                return new SignalDecision(SignalType.BUY, 301, "【戦略301】アセトラブレイク スコア:" + (int)score, tp, sl);
            }
        }

        // --- 302: ディセンディング・トライアングル（売り）の判定 ---
        if (!swings.get(4).isHigh() && !swings.get(2).isHigh() && !swings.get(0).isHigh()) {
            double score = calculateDescendingScore(highs, lows, current, sma50, sma200, key, dataStore);

            if (score >= ENTRY_THRESHOLD) {
                double avgLow = lows.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double recentHigh = highs.get(highs.size() - 1);

                double tp = current.getClose() - (recentHigh - avgLow);
                double sl = recentHigh * 1.01;

                targetPriceMap.put(key, tp); 
                stopLossMap.put(key, sl);
                return new SignalDecision(SignalType.SELL, 302, "【戦略302】ディセトラブレイク スコア:" + (int)score, tp, sl);
            }
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        double tp = targetPriceMap.getOrDefault(key, 0.0);
        double sl = stopLossMap.getOrDefault(key, 0.0);

        if ("LONG".equals(position)) {
            if (tp > 0 && current.getClose() >= tp) { clearPosition(key); return new SignalDecision(SignalType.SELL, 301, "【利確】目標価格到達"); }
            if (sl > 0 && current.getClose() <= sl) { clearPosition(key); return new SignalDecision(SignalType.SELL, 301, "【損切】ストップロス到達"); }
        } else if ("SHORT".equals(position)) {
            if (tp > 0 && current.getClose() <= tp) { clearPosition(key); return new SignalDecision(SignalType.BUY, 302, "【利確】目標価格到達"); }
            if (sl > 0 && current.getClose() >= sl) { clearPosition(key); return new SignalDecision(SignalType.BUY, 302, "【損切】ストップロス到達"); }
        }
        return null;
    }

    private void clearPosition(MarketKey key) {
        targetPriceMap.remove(key);
        stopLossMap.remove(key);
    }

    private double calculateAscendingScore(List<Double> highs, List<Double> lows, CandleData current, 
                                           double sma50, double sma200, MarketKey key, MarketDataStore dataStore) {
        double score = 0.0;
        double resSlope = calculateLinearRegressionSlope(highs);
        double supSlope = calculateLinearRegressionSlope(lows);
        double currentPrice = current.getClose();
        double avgHigh = highs.stream().mapToDouble(Double::doubleValue).average().orElse(currentPrice);

        // 1. 幾何学的評価 (最大40点)
        if (Math.abs(resSlope) < 0.002) score += 20.0; // 上値がほぼ水平
        if (supSlope > 0.001) score += 20.0;           // 下値が切り上がっている

        // 2. ブレイクアウトの強度 (最大30点)
        if (currentPrice > avgHigh * 1.005) score += 15.0; 
        if (currentPrice > avgHigh * 1.01) score += 15.0;

        // 3. 環境認識：上位足トレンド (最大20点)
        if (sma50 > sma200) score += 20.0;

        // 4. 出来高フィルター (最大10点)
        double avgVol = dataStore.getAverageVolume(key, 20);
        if (avgVol > 0 && current.getVolume() > avgVol * 1.5) {
            score += 10.0;
        }

        return score;
    }

    private double calculateDescendingScore(List<Double> highs, List<Double> lows, CandleData current, 
                                            double sma50, double sma200, MarketKey key, MarketDataStore dataStore) {
        double score = 0.0;
        double resSlope = calculateLinearRegressionSlope(highs);
        double supSlope = calculateLinearRegressionSlope(lows);
        double currentPrice = current.getClose();
        double avgLow = lows.stream().mapToDouble(Double::doubleValue).average().orElse(currentPrice);

        // 1. 幾何学的評価 (最大40点)
        if (Math.abs(supSlope) < 0.002) score += 20.0; // 下値がほぼ水平
        if (resSlope < -0.001) score += 20.0;          // 上値が切り下がっている

        // 2. ブレイクアウトの強度 (最大30点)
        if (currentPrice < avgLow * 0.995) score += 15.0;
        if (currentPrice < avgLow * 0.99) score += 15.0; 

        // 3. 環境認識：上位足トレンド (最大20点)
        if (sma50 < sma200) score += 20.0; 

        // 4. 出来高フィルター (最大10点)
        double avgVol = dataStore.getAverageVolume(key, 20);
        if (avgVol > 0 && current.getVolume() > avgVol * 1.5) {
            score += 10.0;
        }

        return score;
    }

    private double calculateLinearRegressionSlope(List<Double> yValues) {
        int n = yValues.size();
        if (n < 2) return 0.0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += yValues.get(i);
            sumXY += i * yValues.get(i);
            sumX2 += i * i;
        }
        double denominator = (n * sumX2 - sumX * sumX);
        if (denominator == 0) return 0.0;
        return (n * sumXY - sumX * sumY) / denominator;
    }
}