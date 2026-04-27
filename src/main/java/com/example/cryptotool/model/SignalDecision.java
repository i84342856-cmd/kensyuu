package com.example.cryptotool.model;

import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;

/**
 * 戦略クラスが判定結果を返すためのレコード（TP/SL対応版）
 */
public record SignalDecision(SignalType type, int strategyId, String reason, double targetPrice, double stopLossPrice) {
    // 古い戦略やTP/SLを使わない戦略のための省略コンストラクタ
    public SignalDecision(SignalType type, int strategyId, String reason) {
        this(type, strategyId, reason, 0.0, 0.0);
    }
}