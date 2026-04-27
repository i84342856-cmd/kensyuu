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
public class SqueezeBreakoutStrategy implements TradingStrategy {
    private final Map<MarketKey, Double> trailingStopMap = new ConcurrentHashMap<>();

    @Override public int getStrategyId() { return 600; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double ema20 = dataStore.getPastEMA(key, 20, 0); double atr20 = dataStore.getATR(key, 20, 0); double std20 = dataStore.getStdDev(key, 20, 0);
        double kcUpper = ema20 + 1.5 * atr20; double bbUpper = ema20 + 2 * std20;
        double kcLower = ema20 - 1.5 * atr20; double bbLower = ema20 - 2 * std20;
        
        double ema20Prev = dataStore.getPastEMA(key, 20, 1); double atr20Prev = dataStore.getATR(key, 20, 1); double std20Prev = dataStore.getStdDev(key, 20, 1);
        double kcUpperPrev = ema20Prev + 1.5 * atr20Prev; double bbUpperPrev = ema20Prev + 2 * std20Prev;
        double kcLowerPrev = ema20Prev - 1.5 * atr20Prev; double bbLowerPrev = ema20Prev - 2 * std20Prev;

        boolean isSqueezePrev = (bbUpperPrev <= kcUpperPrev) && (bbLowerPrev >= kcLowerPrev);
        boolean isSqueezeRelease = (bbUpper > kcUpper) || (bbLower < kcLower);
        
        // 緩和版用の判定（BBとKCの幅が非常に近い状態）
        double bbWidthPrev = bbUpperPrev - bbLowerPrev;
        double kcWidthPrev = kcUpperPrev - kcLowerPrev;
        boolean isNearSqueezePrev = Math.abs(bbWidthPrev - kcWidthPrev) / kcWidthPrev < 0.10;

        // --- 601: 買い ---
        if (isSqueezePrev && isSqueezeRelease && current.getClose() > bbUpper) {
            double sl = current.getClose() - 2.0 * atr20; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.BUY, 601, "【戦略601:通常版】BBKCブレイク(買い)", 0.0, sl);
        }
        if (isNearSqueezePrev && current.getClose() > kcUpper) {
            double sl = current.getClose() - 2.0 * atr20; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.BUY, 601, "【戦略601:緩和版】KCアーリーブレイク(買い)", 0.0, sl);
        }

        // --- 602: 売り ---
        if (isSqueezePrev && isSqueezeRelease && current.getClose() < bbLower) {
            double sl = current.getClose() + 2.0 * atr20; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.SELL, 602, "【戦略602:通常版】BBKCブレイク(売り)", 0.0, sl);
        }
        if (isNearSqueezePrev && current.getClose() < kcLower) {
            double sl = current.getClose() + 2.0 * atr20; trailingStopMap.put(key, sl);
            return new SignalDecision(SignalType.SELL, 602, "【戦略602:緩和版】KCアーリーブレイク(売り)", 0.0, sl);
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        double atr20 = dataStore.getATR(key, 20, 0);
        if ("LONG".equals(position)) {
            double currentSl = trailingStopMap.getOrDefault(key, 0.0);
            double newSl = current.getClose() - 2.0 * atr20;
            if (newSl > currentSl) { trailingStopMap.put(key, newSl); currentSl = newSl; }
            if (current.getClose() <= currentSl) { trailingStopMap.remove(key); return new SignalDecision(SignalType.SELL, 601, "【戦略601利確/損切】ATRトレイリング到達"); }
        } else if ("SHORT".equals(position)) {
            double currentSl = trailingStopMap.getOrDefault(key, Double.MAX_VALUE);
            double newSl = current.getClose() + 2.0 * atr20;
            if (newSl < currentSl) { trailingStopMap.put(key, newSl); currentSl = newSl; }
            if (current.getClose() >= currentSl) { trailingStopMap.remove(key); return new SignalDecision(SignalType.BUY, 602, "【戦略602利確/損切】ATRトレイリング到達"); }
        }
        return null;
    }
}