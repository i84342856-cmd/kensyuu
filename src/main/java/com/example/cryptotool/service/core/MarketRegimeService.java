package com.example.cryptotool.service.core;

import org.springframework.stereotype.Component;

import com.example.cryptotool.infrastructure.MlPredictionClient;
import com.example.cryptotool.model.MarketKey;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MarketRegimeService {
    private final MlPredictionClient mlClient;

    public enum Regime {
        STRONG_TREND_UP, STRONG_TREND_DOWN, RANGE, HIGH_VOLATILITY, UNKNOWN
    }

    // 🛡️【安全対策】通常の戦略クラスから呼ばれても絶対にエラーにならないよう、古いメソッドを残す
    public Regime detectRegime(MarketKey key, MarketDataStore dataStore) {
        return detectRegimeByRule(key, dataStore);
    }

    // 💡【追加】AI戦略用に、特徴量(features)を受け取る新しいメソッド
    public Regime detectRegime(MarketKey key, MarketDataStore dataStore, double[] features) {
        int regimeCode = mlClient.getHmmRegime(features);
        Regime hmmRegime = switch (regimeCode) {
            case 0 -> Regime.RANGE;
            case 1 -> Regime.STRONG_TREND_UP;
            case 2 -> Regime.STRONG_TREND_DOWN;
            case 3 -> Regime.HIGH_VOLATILITY;
            default -> Regime.UNKNOWN;
        };

        if (hmmRegime != Regime.UNKNOWN) {
            return hmmRegime;
        }
        return detectRegimeByRule(key, dataStore);
    }

    private Regime detectRegimeByRule(MarketKey key, MarketDataStore dataStore) {
        double[] adx = dataStore.getADX(key, 14, 0);
        double stdDev = dataStore.getStdDev(key, 20, 0);
        double sma = dataStore.getPastMA(key, 20, 0);
        
        double volRatio = sma > 0 ? (stdDev / sma) * 100 : 0;
        if (volRatio > 2.5) { 
            return Regime.HIGH_VOLATILITY;
        }
        if (adx[0] >= 25) {
            return (adx[1] > adx[2]) ? Regime.STRONG_TREND_UP : Regime.STRONG_TREND_DOWN;
        }
        return Regime.RANGE;
    }
}