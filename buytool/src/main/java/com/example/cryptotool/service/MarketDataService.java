package com.example.cryptotool.service;

import java.util.List; // 追加

import com.example.cryptotool.entity.TradeLog; // 追加
import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;

public interface MarketDataService {
    ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame);
    
    void processRealtimeTick(TickData tick);

    // ★これを追加：履歴取得用のメソッドを定義する
    List<TradeLog> getTradeHistory();
    
 // ★追加: 全件取得用の窓口
    List<TradeLog> getAllTradeHistory();
    
 // チャート初期描画用のマーカー履歴取得
 	List<TradeLog> getTradeLogsForChart(Symbol symbol, TimeFrame timeFrame);
}