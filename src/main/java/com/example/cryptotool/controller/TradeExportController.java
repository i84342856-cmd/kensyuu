package com.example.cryptotool.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.service.MarketDataService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeExportController {

    private final MarketDataService marketDataService;

    @GetMapping("/download")
    public void downloadCsv(HttpServletResponse response) throws IOException {
        // 文字化け対策（UTF-8指定とBOMの追加）
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"full_trade_history.csv\"");

        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');

        // ヘッダー
        writer.println("注文ID,日時,通貨,時間足,売買,価格,数量,戦略ID,損益,メッセージ");

        // 日時フォーマットの定義（日本時間）
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Tokyo"));

        // 全件取得し、古い順から処理するために反転させる
        List<TradeLog> logs = marketDataService.getAllTradeHistory();
        List<TradeLog> ascLogs = new ArrayList<>(logs);
        Collections.reverse(ascLogs);

        // ペアリング用の管理マップと注文IDカウンター
        Map<String, TradeLog> openPositions = new HashMap<>();
        Map<String, Integer> positionIdMap = new HashMap<>();
        int currentOrderId = 1;

        // 出力用の行リスト（最後に最新順に戻すため）
        List<String> csvLines = new ArrayList<>();

        for (TradeLog log : ascLogs) {
            String dateStr = formatter.format(Instant.ofEpochSecond(log.getTime()));
            
            // SYSTEMログの処理
            if ("SYSTEM".equals(log.getSymbol())) {
                csvLines.add(String.format("-,%s,%s,%s,%s,%.2f,%.4f,%d,-,\"%s\"",
                        dateStr, log.getSymbol(), log.getTimeframe(), log.getSide(),
                        log.getPrice(), log.getSize(), log.getStrategy(), log.getMessage().replace("\"", "\"\"")));
                continue;
            }

            String key = log.getSymbol() + "_" + log.getTimeframe();
            String pnlStr = "-";
            String orderIdStr;

            // メッセージ内容から「決済」かどうかを判定
            if (log.getMessage().contains("決済")) {
                TradeLog entry = openPositions.remove(key);
                Integer orderId = positionIdMap.remove(key);
                
                // 注文IDの付与
                orderIdStr = (orderId != null) ? String.valueOf(orderId) : "不明";

                // 損益(PnL)の計算
                if (entry != null) {
                    double pnl = 0.0;
                    if (log.getMessage().contains("LONG決済")) {
                        pnl = (log.getPrice() - entry.getPrice()) * log.getSize();
                    } else if (log.getMessage().contains("SHORT決済")) {
                        pnl = (entry.getPrice() - log.getPrice()) * log.getSize();
                    }
                    pnlStr = String.format("%.2f", pnl);
                }
            } else {
                // エントリーの場合：マップに記録して注文IDを発行
                openPositions.put(key, log);
                positionIdMap.put(key, currentOrderId);
                orderIdStr = String.valueOf(currentOrderId);
                currentOrderId++;
            }

            // CSVの1行を作成
            csvLines.add(String.format("%s,%s,%s,%s,%s,%.2f,%.4f,%d,%s,\"%s\"",
                    orderIdStr, dateStr, log.getSymbol(), log.getTimeframe(), log.getSide(),
                    log.getPrice(), log.getSize(), log.getStrategy(), pnlStr, log.getMessage().replace("\"", "\"\"")));
        }

        // 降順（最新の取引が一番上）に戻して出力
        Collections.reverse(csvLines);
        for (String line : csvLines) {
            writer.println(line);
        }
        
        writer.flush();
    }
}