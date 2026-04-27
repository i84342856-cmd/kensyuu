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
public class BandWalkStrategy implements TradingStrategy {
    private final Map<MarketKey, Integer> activeSubStrategyMap = new ConcurrentHashMap<>();

    @Override public int getStrategyId() { return 200; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double sma = dataStore.getPastMA(key, 20, 0); double smaPrev = dataStore.getPastMA(key, 20, 1);
        double std = dataStore.getStdDev(key, 20, 0); double stdPrev = dataStore.getStdDev(key, 20, 1);
        double upper2 = sma + 2 * std; double lower2 = sma - 2 * std;
        double upper2Prev = smaPrev + 2 * stdPrev; double lower2Prev = smaPrev - 2 * stdPrev;
        double[] adx = dataStore.getADX(key, 14, 0); double[] adxPrev = dataStore.getADX(key, 14, 1);
        double[] macd = dataStore.getMACD(key, 0); double[] macdPrev = dataStore.getMACD(key, 1);
        double prevClose = dataStore.getPastCandleClose(key, 1);

        // --- 201: バンドウォーク順張り ---
        // 買い
        if (prevClose <= upper2Prev && current.getClose() > upper2 && adx[0] >= 20 && adx[0] > adxPrev[0] && macd[2] > 0 && macd[2] > macdPrev[2] && smaPrev > 0 && (sma - smaPrev) / smaPrev > 0.0003) {
            activeSubStrategyMap.put(key, 201); return new SignalDecision(SignalType.BUY, 201, "【戦略201:通常版】+2σ突破(順張り買い)");
        }
        if (prevClose <= upper2Prev && current.getClose() > upper2 && adx[0] >= 15 && smaPrev > 0 && (sma - smaPrev) / smaPrev > 0.0001) {
            activeSubStrategyMap.put(key, 201); return new SignalDecision(SignalType.BUY, 201, "【戦略201:緩和版】+2σ突破(順張り買い)");
        }
        // 売り
        if (prevClose >= lower2Prev && current.getClose() < lower2 && adx[0] >= 20 && adx[0] > adxPrev[0] && macd[2] < 0 && macd[2] < macdPrev[2] && smaPrev > 0 && (sma - smaPrev) / smaPrev < -0.0003) {
            activeSubStrategyMap.put(key, 201); return new SignalDecision(SignalType.SELL, 201, "【戦略201:通常版】-2σ突破(順張り売り)");
        }
        if (prevClose >= lower2Prev && current.getClose() < lower2 && adx[0] >= 15 && smaPrev > 0 && (sma - smaPrev) / smaPrev < -0.0001) {
            activeSubStrategyMap.put(key, 201); return new SignalDecision(SignalType.SELL, 201, "【戦略201:緩和版】-2σ突破(順張り売り)");
        }

        // --- 202: 平均回帰逆張り ---
        // 買い
        if (current.getLow() <= lower2 && adx[0] < 20 && dataStore.isBullishDivergence(key, current, macd[2]) && Math.abs((sma - smaPrev) / smaPrev) < 0.0001) {
            activeSubStrategyMap.put(key, 202); return new SignalDecision(SignalType.BUY, 202, "【戦略202:通常版】-2σ到達(逆張り買い)");
        }
        if (current.getLow() <= lower2 && adx[0] < 25 && Math.abs((sma - smaPrev) / smaPrev) < 0.0002) {
            activeSubStrategyMap.put(key, 202); return new SignalDecision(SignalType.BUY, 202, "【戦略202:緩和版】-2σ到達(逆張り買い)");
        }
        // 売り
        if (current.getHigh() >= upper2 && adx[0] < 20 && dataStore.isBearishDivergence(key, current, macd[2]) && Math.abs((sma - smaPrev) / smaPrev) < 0.0001) {
            activeSubStrategyMap.put(key, 202); return new SignalDecision(SignalType.SELL, 202, "【戦略202:通常版】+2σ到達(逆張り売り)");
        }
        if (current.getHigh() >= upper2 && adx[0] < 25 && Math.abs((sma - smaPrev) / smaPrev) < 0.0002) {
            activeSubStrategyMap.put(key, 202); return new SignalDecision(SignalType.SELL, 202, "【戦略202:緩和版】+2σ到達(逆張り売り)");
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        int subId = activeSubStrategyMap.getOrDefault(key, 201);
        double sma = dataStore.getPastMA(key, 20, 0); double std = dataStore.getStdDev(key, 20, 0);

        if ("LONG".equals(position)) {
            if (subId == 201 && current.getClose() < sma + std) { activeSubStrategyMap.remove(key); return new SignalDecision(SignalType.SELL, 201, "【戦略201利確/損切】モメンタム枯渇(+1σ割れ)"); }
            if (subId == 202 && current.getClose() < sma - 3 * std) { activeSubStrategyMap.remove(key); return new SignalDecision(SignalType.SELL, 202, "【戦略202損切】-3σ割れ(撤退)"); }
            if (subId == 202 && current.getClose() >= sma) { activeSubStrategyMap.remove(key); return new SignalDecision(SignalType.SELL, 202, "【戦略202利確】中心線(20SMA)へ回帰"); }
        } else if ("SHORT".equals(position)) {
            if (subId == 201 && current.getClose() > sma - std) { activeSubStrategyMap.remove(key); return new SignalDecision(SignalType.BUY, 201, "【戦略201利確/損切】モメンタム枯渇(-1σ超え)"); }
            if (subId == 202 && current.getClose() > sma + 3 * std) { activeSubStrategyMap.remove(key); return new SignalDecision(SignalType.BUY, 202, "【戦略202損切】+3σ超え(撤退)"); }
            if (subId == 202 && current.getClose() <= sma) { activeSubStrategyMap.remove(key); return new SignalDecision(SignalType.BUY, 202, "【戦略202利確】中心線(20SMA)へ回帰"); }
        }
        return null;
    }
}