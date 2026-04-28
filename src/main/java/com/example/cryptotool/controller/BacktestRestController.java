package com.example.cryptotool.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cryptotool.model.BacktestResult;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.service.backtest.BacktestEngineService;

@RestController
@RequestMapping("/api/backtest")
public class BacktestRestController {

    private final BacktestEngineService backtestEngineService;

    public BacktestRestController(BacktestEngineService backtestEngineService) {
        this.backtestEngineService = backtestEngineService;
    }

    /**
     * ブラウザからテストを実行するエンドポイント
     * 例: http://localhost:8080/api/backtest/run?symbol=BTC_JPY&timeframe=M15&strategyId=500&candles=2000
     */
    @GetMapping("/run")
    public BacktestResult runBacktest(
            @RequestParam Symbol symbol,
            @RequestParam TimeFrame timeframe,
            @RequestParam int strategyId,
            @RequestParam(defaultValue = "2000") int candles) {
        
        return backtestEngineService.runBacktest(symbol, timeframe, strategyId, candles);
    }
}