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

    // 現在の市場レジーム（状態）を判定する
    public Regime detectRegime(MarketKey key, MarketDataStore dataStore) {
        // 1. HMM用観測データの準備（収益率とボラティリティ）
        double[] observations = new double[2];
        double close0 = dataStore.getPastCandleClose(key, 0);
        double close1 = dataStore.getPastCandleClose(key, 1);
        
        if (close1 > 0) {
            observations[0] = (close0 - close1) / close1;
        }
        observations[1] = dataStore.getStdDev(key, 20, 0);

        // 2. HMM APIによるレジーム判定リクエスト
        int regimeCode = mlClient.getHmmRegime(observations);
        Regime hmmRegime = switch (regimeCode) {
            case 0 -> Regime.RANGE;
            case 1 -> Regime.STRONG_TREND_UP;
            case 2 -> Regime.STRONG_TREND_DOWN;
            case 3 -> Regime.HIGH_VOLATILITY;
            default -> Regime.UNKNOWN;
        };

        // 3. API通信成功かつ判定可能な場合はHMMの結果を採用
        if (hmmRegime != Regime.UNKNOWN) {
            return hmmRegime;
        }

        // 4. APIエラー時（UNKNOWN）は従来のルールベース判定にフォールバック
        return detectRegimeByRule(key, dataStore);
    }

    // 従来のルール（ADX・ボラティリティ）を用いた判定ロジック
    private Regime detectRegimeByRule(MarketKey key, MarketDataStore dataStore) {
        double[] adx = dataStore.getADX(key, 14, 0);
        double stdDev = dataStore.getStdDev(key, 20, 0);
        double sma = dataStore.getPastMA(key, 20, 0);
        
        // ボラティリティの急増判定
        double volRatio = sma > 0 ? (stdDev / sma) * 100 : 0;
        if (volRatio > 2.5) { 
            return Regime.HIGH_VOLATILITY;
        }

        // ADX(トレンドの強さ)を用いた判定
        if (adx[0] >= 25) {
            return (adx[1] > adx[2]) ? Regime.STRONG_TREND_UP : Regime.STRONG_TREND_DOWN;
        }
        return Regime.RANGE;
    }
}