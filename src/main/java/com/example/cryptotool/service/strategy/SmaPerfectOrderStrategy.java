package com.example.cryptotool.service.strategy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;

@Component
public class SmaPerfectOrderStrategy implements TradingStrategy {
    private final Map<MarketKey, Double> trailingStopMap = new ConcurrentHashMap<>();

    @Override public int getStrategyId() { return 400; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double sma = dataStore.getPastMA(key, 20, 0); double smaPrev = dataStore.getPastMA(key, 20, 1);
        double sma50 = dataStore.getPastMA(key, 50, 0); double ma50Prev = dataStore.getPastMA(key, 50, 1);
        double sma200 = dataStore.getPastMA(key, 200, 0);
        double ma5 = dataStore.getPastMA(key, 5, 0); double ma5Prev = dataStore.getPastMA(key, 5, 1);
        double[] adx = dataStore.getADX(key, 14, 0);
        double prevClose = dataStore.getPastCandleClose(key, 1);
        double atr = dataStore.getATR(key, 14, 0);

        boolean isBreakoutUp = prevClose <= ma5Prev && current.getClose() > ma5;
        boolean isBreakoutDown = prevClose >= ma5Prev && current.getClose() < ma5;

        // --- 401: 買い (LONG) ---
        if (sma > sma50 && sma50 > sma200 && sma > smaPrev && sma50 > ma50Prev && isBreakoutUp && adx[0] >= 15) {
            double sl = current.getClose() - 2 * atr; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.BUY, 401, "【戦略401:通常版】SMA PO順張り(買い)", 0.0, sl);
        }
        if (sma > sma50 && sma > smaPrev && isBreakoutUp && adx[0] >= 10) {
            double sl = current.getClose() - 2 * atr; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.BUY, 401, "【戦略401:緩和版】短期SMA PO順張り(買い)", 0.0, sl);
        }

        // --- 402: 売り (SHORT) ---
        if (sma < sma50 && sma50 < sma200 && sma < smaPrev && sma50 < ma50Prev && isBreakoutDown && adx[0] >= 15) {
            double sl = current.getClose() + 2 * atr; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.SELL, 402, "【戦略402:通常版】SMA PO順張り(売り)", 0.0, sl);
        }
        if (sma < sma50 && sma < smaPrev && isBreakoutDown && adx[0] >= 10) {
            double sl = current.getClose() + 2 * atr; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.SELL, 402, "【戦略402:緩和版】短期SMA PO順張り(売り)", 0.0, sl);
        }
        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        double atr = dataStore.getATR(key, 14, 0);
        if ("LONG".equals(position)) {
            double currentSl = trailingStopMap.getOrDefault(key, 0.0);
            double newSl = current.getClose() - 2 * atr;
            if (newSl > currentSl) { trailingStopMap.put(key, newSl); currentSl = newSl; }
            if (current.getClose() <= currentSl) {
                trailingStopMap.remove(key); return new SignalDecision(SignalType.SELL, 401, "【戦略401利確/損切】トレイリング到達");
            }
        } else if ("SHORT".equals(position)) {
            double currentSl = trailingStopMap.getOrDefault(key, Double.MAX_VALUE);
            double newSl = current.getClose() + 2 * atr;
            if (newSl < currentSl) { trailingStopMap.put(key, newSl); currentSl = newSl; }
            if (current.getClose() >= currentSl) {
                trailingStopMap.remove(key); return new SignalDecision(SignalType.BUY, 402, "【戦略402利確/損切】トレイリング到達");
            }
        }
        return null;
    }
}