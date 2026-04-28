package com.example.cryptotool.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RealtimeUpdateDto {
    private ChartInitResponse.CandleData currentCandle;
    private double currentMa5;
    private double currentMa10;
    private double currentMa25;
    private double currentMa50;
    private double currentMa75;
    private double currentMa100;
    private SignalType signal; // BUY, SELL, NONE
    
 // 既存のフィールドの下に、以下の2行を追加してください
    private Double supportLine;    // 戦略8: 支持線（下部のトレンド線）
    private Double resistanceLine; // 戦略8: 抵抗線（上部のトレンド線）
    
 // AI予測とレジーム判定用のフィールドを追加
    private Double aiUpProbability;
    private String marketRegime;

    public enum SignalType {
        BUY, SELL, NONE
    }
}