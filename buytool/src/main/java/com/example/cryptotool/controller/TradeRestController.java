package com.example.cryptotool.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.service.impl.MarketDataServiceImpl;

@RestController
public class TradeRestController {

    private final MarketDataServiceImpl marketDataService;

    public TradeRestController(MarketDataServiceImpl marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/api/trades/history")
    public List<TradeLog> getTradeHistory() {
        return marketDataService.getTradeHistory();
    }
    
    @GetMapping("/api/trades/chart")
    public List<TradeLog> getChartTrades(@RequestParam Symbol symbol, @RequestParam TimeFrame timeframe) {
        return marketDataService.getTradeLogsForChart(symbol, timeframe);
    }
    
    // --- 監視設定 (Monitor Settings) ---
    
    @GetMapping("/api/settings/monitor")
    public Map<String, Boolean> getMonitorSettings() {
        return marketDataService.getMonitorSettings();
    }

    @PostMapping("/api/settings/monitor")
    public void updateSetting(@RequestParam String symbol, @RequestParam String timeframe, @RequestParam boolean active) {
        marketDataService.updateMonitorSetting(symbol, timeframe, active);
    }

    // --- 戦略設定 (Strategy Settings) ---
    
    @GetMapping("/api/settings/strategy")
    public Map<String, Boolean> getStrategySettings() {
        return marketDataService.getStrategySettings();
    }

    @PostMapping("/api/settings/strategy")
    public void updateStrategySetting(@RequestParam String id, @RequestParam boolean active) {
        marketDataService.updateStrategySetting(id, active);
    }
}