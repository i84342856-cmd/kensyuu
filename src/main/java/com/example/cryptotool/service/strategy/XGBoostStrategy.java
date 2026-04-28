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
 * 【戦略700系】XGBoostによる予測モデリング戦略
 */
@Component
@RequiredArgsConstructor
public class XGBoostStrategy implements TradingStrategy {

    private final MlPredictionClient mlClient;
    private static final double PROBABILITY_THRESHOLD_BUY = 0.75;
    private static final double PROBABILITY_THRESHOLD_SELL = 0.25;

    @Override
    public int getStrategyId() { return 700; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double[] features = buildFeatureVector(key, current, dataStore);
        double upProbability = mlClient.getPredictionProbability(features);
        
     // ▼▼▼ ここに追記（ログ出力） ▼▼▼
        System.out.println(String.format("【AI推論】%s - 上昇確率: %.2f", key, upProbability));
        // ▲▲▲ ここまで ▲▲▲
        
        if (upProbability >= PROBABILITY_THRESHOLD_BUY) {
            double sl = current.getClose() - (dataStore.getATR(key, 14, 0) * 2);
            return new SignalDecision(SignalType.BUY, 701, "【戦略701】XGBoost上昇予測(" + String.format("%.2f", upProbability) + ")", 0.0, sl);
        }

        if (upProbability <= PROBABILITY_THRESHOLD_SELL) {
            double sl = current.getClose() + (dataStore.getATR(key, 14, 0) * 2);
            return new SignalDecision(SignalType.SELL, 702, "【戦略702】XGBoost下落予測(" + String.format("%.2f", upProbability) + ")", 0.0, sl);
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        double[] features = buildFeatureVector(key, current, dataStore);
        double upProbability = mlClient.getPredictionProbability(features);

        if ("LONG".equals(position) && upProbability < 0.40) {
            return new SignalDecision(SignalType.SELL, 701, "【利確/損切】予測確率低下によるエグジット");
        } else if ("SHORT".equals(position) && upProbability > 0.60) {
            return new SignalDecision(SignalType.BUY, 702, "【利確/損切】予測確率上昇によるエグジット");
        }
        return null;
    }

    private double[] buildFeatureVector(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double rsi = dataStore.getRSI(key, 14, 0);
        double[] macd = dataStore.getMACD(key, 0);
        double maDev = dataStore.getMaDeviationRate(key, 20, 0);
        double stdDev = dataStore.getStdDev(key, 20, 0);
        double bbWidth = (stdDev * 4) / dataStore.getPastMA(key, 20, 0);
        double[] adx = dataStore.getADX(key, 14, 0);

        // 特徴量ベクトル X_t を構築
        return new double[]{ rsi, macd[0], macd[1], macd[2], maDev, bbWidth, adx[0], adx[1], adx[2] };
    }
}