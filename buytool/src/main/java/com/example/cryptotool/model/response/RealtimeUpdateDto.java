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
    private double currentMa100;
    private SignalType signal; // BUY, SELL, NONE

    public enum SignalType {
        BUY, SELL, NONE
    }
}