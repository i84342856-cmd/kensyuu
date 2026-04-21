package com.example.cryptotool.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;
import com.example.cryptotool.service.MarketDataService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chart")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // フロントエンドからのCORSリクエストを許可
public class ChartRestController {

    // 実際のロジックを担うサービスクラス（コンストラクタインジェクション）
    private final MarketDataService marketDataService;

    /**
     * 指定された通貨ペアと時間足の初期チャートデータ（過去のローソク足＋MA）を取得する
     * GET /api/chart/init?symbol=BTC_JPY&timeframe=M5
     */
    @GetMapping("/init")
    public ResponseEntity<ChartInitResponse> getInitialChartData(
            @RequestParam("symbol") Symbol symbol,
            @RequestParam("timeframe") TimeFrame timeFrame) {
        
        ChartInitResponse response = marketDataService.getInitialData(symbol, timeFrame);
        return ResponseEntity.ok(response);
    }

    /**
     * 監視対象の通貨ペア一覧を取得する
     * GET /api/chart/symbols
     */
    @GetMapping("/symbols")
    public ResponseEntity<Symbol[]> getAvailableSymbols() {
        return ResponseEntity.ok(Symbol.values());
    }

    /**
     * 選択可能な時間足一覧を取得する
     * GET /api/chart/timeframes
     */
    @GetMapping("/timeframes")
    public ResponseEntity<TimeFrame[]> getAvailableTimeFrames() {
        return ResponseEntity.ok(TimeFrame.values());
    }
}