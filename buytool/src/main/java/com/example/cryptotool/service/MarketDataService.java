package com.example.cryptotool.service;

import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;

public interface MarketDataService {
    ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame);
    
 // ★修正: TimeFrameの引数を削除し、TickDataだけで全足を処理する形に変更
    void processRealtimeTick(TickData tick);
}