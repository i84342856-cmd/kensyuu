package com.example.cryptotool.service.strategy;

import org.springframework.stereotype.Component;

import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.service.core.MarketDataStore;

/**
 * 【戦略1】MA5抜け ＋ MA25トレンド ＋ ボリンジャーバンドボラティリティ
 */
@Component
public class MaCrossStrategy implements TradingStrategy {

    // 通常版の厳しい条件
    private final double MIN_TREND_SLOPE_NORMAL = 0.0003; 
    private final double MIN_BB_WIDTH_NORMAL = 0.005;     
    
    // 緩和版のゆるい条件
    private final double MIN_TREND_SLOPE_RELAXED = 0.0001; 
    private final double MIN_BB_WIDTH_RELAXED = 0.003;     

    @Override
    public int getStrategyId() {
        return 1;
    }

    @Override
    public SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore) {
        // --- データの取得 ---
        double c1_close = dataStore.getPastCandleClose(key, 1);
        double c2_close = dataStore.getPastCandleClose(key, 2);
        double c1_ma5 = dataStore.getPastMA(key, 5, 1);
        double c2_ma5 = dataStore.getPastMA(key, 5, 2);
        double c1_ma25 = dataStore.getPastMA(key, 25, 1);
        double c2_ma25 = dataStore.getPastMA(key, 25, 2);
        double sma20 = dataStore.getPastMA(key, 20, 1);
        double stdDev20 = dataStore.getStdDev(key, 20, 1);
        double bandWidth = (sma20 > 0) ? (4 * stdDev20) / sma20 : 0;

        // ==========================================
        // 買いシグナル判定
        // ==========================================
        boolean crossUp = (c2_close <= c2_ma5) && (c1_close > c1_ma5);

        // 【1. 通常版の判定】
        boolean isTrendUp_Normal = c2_ma25 > 0 && ((c1_ma25 - c2_ma25) / c2_ma25) >= MIN_TREND_SLOPE_NORMAL;
        boolean isVolatile_Normal = bandWidth >= MIN_BB_WIDTH_NORMAL;

        if (crossUp && isTrendUp_Normal && isVolatile_Normal) {
            return new SignalDecision(SignalType.BUY, getStrategyId(), "【戦略1:通常版】MA5上抜け ＋ トレンド上向き(厳格)");
        }

        // 【2. 緩和版の判定】
        boolean isTrendUp_Relaxed = c2_ma25 > 0 && ((c1_ma25 - c2_ma25) / c2_ma25) >= MIN_TREND_SLOPE_RELAXED;
        boolean isVolatile_Relaxed = bandWidth >= MIN_BB_WIDTH_RELAXED;

        if (crossUp && isTrendUp_Relaxed && isVolatile_Relaxed) {
            return new SignalDecision(SignalType.BUY, getStrategyId(), "【戦略1:緩和版】MA5上抜け ＋ トレンド微増(緩和)");
        }

        // ==========================================
        // 売りシグナル判定
        // ==========================================
        boolean crossDown = (c2_close >= c2_ma5) && (c1_close < c1_ma5);

        // 【1. 通常版の判定】
        boolean isTrendDown_Normal = c2_ma25 > 0 && ((c1_ma25 - c2_ma25) / c2_ma25) <= -MIN_TREND_SLOPE_NORMAL;

        if (crossDown && isTrendDown_Normal && isVolatile_Normal) {
            return new SignalDecision(SignalType.SELL, getStrategyId(), "【戦略1:通常版】MA5下抜け ＋ トレンド下向き(厳格)");
        }

        // 【2. 緩和版の判定】
        boolean isTrendDown_Relaxed = c2_ma25 > 0 && ((c1_ma25 - c2_ma25) / c2_ma25) <= -MIN_TREND_SLOPE_RELAXED;

        if (crossDown && isTrendDown_Relaxed && isVolatile_Relaxed) {
            return new SignalDecision(SignalType.SELL, getStrategyId(), "【戦略1:緩和版】MA5下抜け ＋ トレンド微減(緩和)");
        }

        return null;
    }

    @Override
    public SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, 
                                    String currentPosition, double entryPrice, long entryTime) {
        if (entryPrice <= 0) return null;

        double slPct = getStopLossPercentage(key.timeFrame());
        long c1_time = dataStore.getPastCandleTime(key, 1);

        if ("LONG".equals(currentPosition)) {
            // 損切り
            if (current.getClose() <= entryPrice * (1.0 - slPct)) {
                return new SignalDecision(SignalType.SELL, getStrategyId(), String.format("【戦略1:損切り】設定幅(%.1f%%)を超過", slPct * 100));
            }
            // 初陰線決済
            if (c1_time > entryTime) {
                if (dataStore.getPastCandleClose(key, 1) < dataStore.getPastCandleOpen(key, 1)) {
                    return new SignalDecision(SignalType.SELL, getStrategyId(), "【戦略1:決済】初陰線の確定");
                }
            }
        } else if ("SHORT".equals(currentPosition)) {
            // 損切り
            if (current.getClose() >= entryPrice * (1.0 + slPct)) {
                return new SignalDecision(SignalType.BUY, getStrategyId(), String.format("【戦略1:損切り】設定幅(%.1f%%)を超過", slPct * 100));
            }
            // 初陽線決済
            if (c1_time > entryTime) {
                if (dataStore.getPastCandleClose(key, 1) > dataStore.getPastCandleOpen(key, 1)) {
                    return new SignalDecision(SignalType.BUY, getStrategyId(), "【戦略1:決済】初陽線の確定");
                }
            }
        }

        return null;
    }

    private double getStopLossPercentage(TimeFrame tf) {
        switch (tf.name()) {
            case "M5": return 0.003;
            case "M15": return 0.005;
            case "M30": return 0.010;
            case "H1": return 0.015;
            case "H4": return 0.020;
            default: return 0.010;
        }
    }
}