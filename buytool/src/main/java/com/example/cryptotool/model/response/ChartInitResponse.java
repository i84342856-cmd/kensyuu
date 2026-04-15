package com.example.cryptotool.model.response;

import java.util.List;

import lombok.AllArgsConstructor; // ★追加
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; // ★追加

@Data
@Builder
@NoArgsConstructor // ★追加
@AllArgsConstructor // ★追加
public class ChartInitResponse {
    private List<CandleData> candles;
    private List<MovingAverageData> ma5;
    private List<MovingAverageData> ma10;
    private List<MovingAverageData> ma25;
    private List<MovingAverageData> ma50;
    private List<MovingAverageData> ma100;

    @Data
    @Builder
    @NoArgsConstructor // ★追加
    @AllArgsConstructor // ★追加
    public static class CandleData {
        private long time;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;
    }

    @Data
    @Builder
    @NoArgsConstructor // ★追加
    @AllArgsConstructor // ★追加
    public static class MovingAverageData {
        private long time;
        private double value;
    }
}