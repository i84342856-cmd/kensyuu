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
 * 【戦略800系】XGBoost予測 ＋ HMM(市場レジーム)フィルター戦略
 */
@Component
@RequiredArgsConstructor
public class XgbHmmStrategy implements TradingStrategy {

    private final MlPredictionClient mlClient;
    // ▼ HMMの判定結果を取得するために追加
    private final MarketRegimeService regimeService; 

    private static final double PROBABILITY_THRESHOLD_BUY = 0.75;
    private static final double PROBABILITY_THRESHOLD_SELL = 0.25;

    @Override
    public int getStrategyId() { return 800; }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double[] features = buildFeatureVector(key, current, dataStore);
        double upProbability = mlClient.getPredictionProbability(features);
        
        // ▼▼▼ HMMによる市場レジームの取得 ▼▼▼
        String regimeName = regimeService.detectRegime(key, dataStore).name();
        
        System.out.println(String.format("【AI推論/戦略800】%s - 上昇確率: %.2f / レジーム: %s", key, upProbability, regimeName));

        // 💡【HMMフィルター】レンジ相場なら、XGBoostがなんと言おうとダマシ回避のため見送り！
        if ("RANGE".equals(regimeName)) {
            return null; 
        }

        // HMMのフィルターを通過した場合のみ、XGBoostの確率で売買判定
        if (upProbability >= PROBABILITY_THRESHOLD_BUY) {
            double sl = current.getClose() - (dataStore.getATR(key, 14, 0) * 2);
            return new SignalDecision(SignalType.BUY, 801, "【戦略801】XGBoost上昇予測(" + String.format("%.2f", upProbability) + ") + トレンド確認", 0.0, sl);
        }

        if (upProbability <= PROBABILITY_THRESHOLD_SELL) {
            double sl = current.getClose() + (dataStore.getATR(key, 14, 0) * 2);
            return new SignalDecision(SignalType.SELL, 802, "【戦略802】XGBoost下落予測(" + String.format("%.2f", upProbability) + ") + トレンド確認", 0.0, sl);
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, String position, double entryPrice, long entryTime) {
        double[] features = buildFeatureVector(key, current, dataStore);
        double upProbability = mlClient.getPredictionProbability(features);

        if ("LONG".equals(position) && upProbability < 0.40) {
            return new SignalDecision(SignalType.SELL, 801, "【利確/損切】予測確率低下によるエグジット");
        } else if ("SHORT".equals(position) && upProbability > 0.60) {
            return new SignalDecision(SignalType.BUY, 802, "【利確/損切】予測確率上昇によるエグジット");
        }
        return null;
    }

    // ▼ Python側の新しいAIモデルに合わせて、特徴量の計算式を最新版（普遍的パーセント）に修正
    private double[] buildFeatureVector(MarketKey key, CandleData current, MarketDataStore dataStore) {
        double rsi = dataStore.getRSI(key, 14, 0);
        double stdDev = dataStore.getStdDev(key, 20, 0);
        double maDev = dataStore.getMaDeviationRate(key, 20, 0);
        
        double ma5 = dataStore.getPastMA(key, 5, 0);
        double ma25 = dataStore.getPastMA(key, 25, 0);
        double f4_macd = (ma25 != 0) ? ((ma5 - ma25) / ma25) * 100 : 0; 
        
        double open = current.getOpen();
        double close = current.getClose();
        double high = current.getHigh();
        double low = current.getLow();
        double f5_body = (close != 0) ? (Math.abs(close - open) / close) * 100 : 0; 
        double f6_upper = (close != 0) ? ((high - Math.max(open, close)) / close) * 100 : 0; 
        double f7_lower = (close != 0) ? ((Math.min(open, close) - low) / close) * 100 : 0; 
        
        double stdDevPct = (close != 0) ? (stdDev / close) * 100 : 0;
        
        double prevVol = 0;
        java.util.List<CandleData> history = dataStore.getHistoryMap().get(key);
        if (history != null && !history.isEmpty()) {
            prevVol = history.get(history.size() - 1).getVolume();
        }
        double volRatio = (prevVol != 0) ? (current.getVolume() / prevVol) * 100 : 100;
        
        // 💡【エラー解消】keyの文字列（例: BTC_JPY_H1）の末尾から時間足を判定する
        double tfValue = 0;
        String keyStr = key.toString(); // 例: "FX_BTC_JPY_H1"
        
        if (keyStr.endsWith("M5")) tfValue = 5;
        else if (keyStr.endsWith("M15")) tfValue = 15;
        else if (keyStr.endsWith("M30")) tfValue = 30;
        else if (keyStr.endsWith("H1")) tfValue = 60;
        else if (keyStr.endsWith("H4")) tfValue = 240;
        else if (keyStr.endsWith("D1")) tfValue = 1440;
        else if (keyStr.endsWith("W1")) tfValue = 10080;

        // 最後の枠に `tfValue` をセットして送信
        return new double[]{ rsi, stdDevPct, maDev, f4_macd, f5_body, f6_upper, f7_lower, volRatio, tfValue };
    }
}