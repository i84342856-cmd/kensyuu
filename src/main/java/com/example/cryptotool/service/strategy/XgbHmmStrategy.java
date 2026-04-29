package com.example.cryptotool.service.strategy;

import org.springframework.stereotype.Component;

import com.example.cryptotool.infrastructure.MlPredictionClient;
import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;
import com.example.cryptotool.service.core.MarketRegimeService;

import lombok.RequiredArgsConstructor;

/**
 * 【戦略800】XGBoost + HMM多重フィルター戦略
 * トレンド発生中のみエントリーを許可し、無駄な負けを徹底排除する完成形
 */
@Component
@RequiredArgsConstructor
public class XgbHmmStrategy implements TradingStrategy {

    private final MlPredictionClient mlClient;
    private final MarketRegimeService regimeService; 
    private final XGBoostStrategy xgboostBase; // buildFeatureVectorの共通利用のため

    @Override public int getStrategyId() { return 800; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double[] features = xgboostBase.buildFeatureVector(key, current, dataStore);
        double upProbability = mlClient.getPredictionProbability(features);
        
        // 💡 HMMレジーム判定を取得
        String regimeName = regimeService.detectRegime(key, dataStore, features).name();
        
        // 💡 【超重要】強いトレンドの時だけ取引を許可。これで戦略700と明確に差が出る。
        if (!(regimeName.contains("STRONG_TREND"))) {
            return null; 
        }

        double atr = dataStore.getATR(key, 14, 0);
        // トレンド時は利益を伸ばすため、利確をATR 2.0倍に設定
        if (upProbability >= 0.60) {
            double tp = current.getClose() + (atr * 2.0);
            double sl = current.getClose() - (atr * 1.0);
            return new SignalDecision(SignalType.BUY, 801, "強トレンド確認 + AI上昇(" + String.format("%.2f", upProbability) + ")", tp, sl);
        }
        if (upProbability <= 0.40) {
            double tp = current.getClose() - (atr * 2.0);
            double sl = current.getClose() + (atr * 1.0);
            return new SignalDecision(SignalType.SELL, 802, "強トレンド確認 + AI下落(" + String.format("%.2f", upProbability) + ")", tp, sl);
        }
        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        return null; // 指値決済に任せる
    }
}