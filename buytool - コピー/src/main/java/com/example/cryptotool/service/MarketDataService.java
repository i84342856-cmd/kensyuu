package com.example.cryptotool.service;

import java.util.List;
import java.util.Map;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;

public interface MarketDataService {
    ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame);
    
    void processRealtimeTick(TickData tick);

    // 履歴取得用のメソッド
    List<TradeLog> getTradeHistory();
    
    // 全件取得用の窓口
    List<TradeLog> getAllTradeHistory();
    
    // チャート初期描画用のマーカー履歴取得
    List<TradeLog> getTradeLogsForChart(Symbol symbol, TimeFrame timeFrame);

    // 監視設定（ON/OFF）の取得と更新
    Map<String, Boolean> getMonitorSettings();
    void updateMonitorSetting(String symbol, String timeframe, boolean active);

    // 戦略設定（ON/OFF）の取得と更新
    Map<String, Boolean> getStrategySettings();
    void updateStrategySetting(String id, boolean active);
}