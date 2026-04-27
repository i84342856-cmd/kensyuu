package com.example.cryptotool.service.strategy;

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

@Component
public class TriangleBreakoutStrategy implements TradingStrategy {
    private final Map<MarketKey, Double> targetPriceMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Double> stopLossMap = new ConcurrentHashMap<>();

    @Override public int getStrategyId() { return 300; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        List<SwingPoint> swings = dataStore.getRecentSwings(key, 5);
        if (swings == null || swings.size() < 5) return null;

        double e1 = swings.get(0).price(); double e2 = swings.get(1).price();
        double e3 = swings.get(2).price(); double e4 = swings.get(3).price(); double e5 = swings.get(4).price();
        double sma50 = dataStore.getPastMA(key, 50, 0); double sma200 = dataStore.getPastMA(key, 200, 0);

        // --- 301: アセトラブレイク（買い） ---
        if (swings.get(4).isHigh() && !swings.get(3).isHigh() && swings.get(2).isHigh() && !swings.get(1).isHigh() && swings.get(0).isHigh()) {
            double maxHigh = Math.max(e1, Math.max(e3, e5)); double minHigh = Math.min(e1, Math.min(e3, e5)); double avgHigh = (e1 + e3 + e5) / 3.0;
            
            // 通常版
            if ((maxHigh - minHigh) / avgHigh <= 0.015 && e2 > e4 && current.getClose() > maxHigh * 1.01 && sma50 > sma200) {
                double tp = current.getClose() + (avgHigh - e2); double sl = e4 * 0.99;
                targetPriceMap.put(key, tp); stopLossMap.put(key, sl);
                return new SignalDecision(SignalType.BUY, 301, "【戦略301:通常版】アセトラブレイクアウト(買い)", tp, sl);
            }
            // 緩和版
            if ((maxHigh - minHigh) / avgHigh <= 0.025 && e2 > e4 && current.getClose() > maxHigh * 1.005) {
                double tp = current.getClose() + (avgHigh - e2); double sl = e4 * 0.99;
                targetPriceMap.put(key, tp); stopLossMap.put(key, sl);
                return new SignalDecision(SignalType.BUY, 301, "【戦略301:緩和版】アセトラブレイクアウト(買い)", tp, sl);
            }
        }
        
        // --- 302: ディセトラブレイク（売り） ---
        if (!swings.get(4).isHigh() && swings.get(3).isHigh() && !swings.get(2).isHigh() && swings.get(1).isHigh() && !swings.get(0).isHigh()) {
            double maxLow = Math.max(e1, Math.max(e3, e5)); double minLow = Math.min(e1, Math.min(e3, e5)); double avgLow = (e1 + e3 + e5) / 3.0;
            
            // 通常版
            if ((maxLow - minLow) / avgLow <= 0.015 && e2 < e4 && current.getClose() < minLow * 0.99 && sma50 < sma200) {
                double tp = current.getClose() - (e2 - avgLow); double sl = e4 * 1.01;
                targetPriceMap.put(key, tp); stopLossMap.put(key, sl);
                return new SignalDecision(SignalType.SELL, 302, "【戦略302:通常版】ディセトラブレイク(売り)", tp, sl);
            }
            // 緩和版
            if ((maxLow - minLow) / avgLow <= 0.025 && e2 < e4 && current.getClose() < minLow * 0.995) {
                double tp = current.getClose() - (e2 - avgLow); double sl = e4 * 1.01;
                targetPriceMap.put(key, tp); stopLossMap.put(key, sl);
                return new SignalDecision(SignalType.SELL, 302, "【戦略302:緩和版】ディセトラブレイク(売り)", tp, sl);
            }
        }
        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        double tp = targetPriceMap.getOrDefault(key, 0.0);
        double sl = stopLossMap.getOrDefault(key, 0.0);

        if ("LONG".equals(position)) {
            if (tp > 0 && current.getClose() >= tp) { targetPriceMap.remove(key); stopLossMap.remove(key); return new SignalDecision(SignalType.SELL, 301, "【戦略301利確】目標価格(TP)到達"); }
            if (sl > 0 && current.getClose() <= sl) { targetPriceMap.remove(key); stopLossMap.remove(key); return new SignalDecision(SignalType.SELL, 301, "【戦略301損切】ストップロス(SL)到達"); }
        } else if ("SHORT".equals(position)) {
            if (tp > 0 && current.getClose() <= tp) { targetPriceMap.remove(key); stopLossMap.remove(key); return new SignalDecision(SignalType.BUY, 302, "【戦略302利確】目標価格(TP)到達"); }
            if (sl > 0 && current.getClose() >= sl) { targetPriceMap.remove(key); stopLossMap.remove(key); return new SignalDecision(SignalType.BUY, 302, "【戦略302損切】ストップロス(SL)到達"); }
        }
        return null;
    }
}