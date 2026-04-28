package com.example.cryptotool.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;

import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class CryptoCompareClient {

    private final WebClient webClient;

    public CryptoCompareClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://min-api.cryptocompare.com").build();
    }

    public List<CandleData> getHistoricalCandles(Symbol symbol, TimeFrame tf, int limit) {
        // ★修正: 分足は過去7日間(10080分)しか取得できない制限を回避するため、リミットを動的調整
        int maxLimit = 2000;
        if (tf == TimeFrame.M5) maxLimit = 2000;       // 10080 / 5 = 2016
        else if (tf == TimeFrame.M15) maxLimit = 670;  // 10080 / 15 = 672
        else if (tf == TimeFrame.M30) maxLimit = 330;  // 10080 / 30 = 336
        
        int safeLimit = Math.min(limit, maxLimit); 

        // ★修正: "FX_BTC_JPY" などの場合に "FX" を除外してベース通貨を取得
        String fsym = symbol.name().replace("FX_", "").split("_")[0];
        String tsym = "JPY";
        String endpoint = getEndpoint(tf);
        int aggregate = getAggregate(tf); 

        try {
            log.info("🌐 APIリクエスト: {} {} ({}本要求 -> {}本に調整, まとめる間隔={})", fsym, tf, limit, safeLimit, aggregate);

            return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(endpoint)
                    .queryParam("fsym", fsym)
                    .queryParam("tsym", tsym)
                    .queryParam("limit", safeLimit)
                    .queryParam("aggregate", aggregate) 
                    .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> {
                    if (node.has("Response") && "Error".equals(node.get("Response").asText())) {
                        String errMsg = node.get("Message").asText();
                        log.error("APIエラーレスポンス: {}", errMsg);
                        throw new RuntimeException("API制限またはエラー: " + errMsg);
                    }
                    
                    List<CandleData> list = new ArrayList<>();
                    if (node.has("Data") && node.get("Data").has("Data")) {
                        node.get("Data").get("Data").forEach(c -> {
                            list.add(CandleData.builder()
                                .time(c.get("time").asLong())
                                .open(c.get("open").asDouble())
                                .high(c.get("high").asDouble())
                                .low(c.get("low").asDouble())
                                .close(c.get("close").asDouble())
                                .volume(c.get("volumeto").asDouble())
                                .build());
                        });
                    }
                    return list;
                })
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(3))
                    .doBeforeRetry(retrySignal -> log.warn("⚠️ API再試行中... (回数: {})", retrySignal.totalRetries() + 1)))
                .block(Duration.ofSeconds(15));

        } catch (Exception e) {
            log.error("❌ API取得に失敗しました: {}", e.getMessage());
            return new ArrayList<>(); 
        }
    }

    private String getEndpoint(TimeFrame tf) {
        return switch (tf) {
            case M5, M15, M30 -> "/data/v2/histominute";
            case H1, H4 -> "/data/v2/histohour"; // ★H4を追加
            default -> "/data/v2/histoday";
        };
    }

    private int getAggregate(TimeFrame tf) {
        return switch (tf) {
            case M5 -> 5;
            case M15 -> 15;
            case M30 -> 30;
            case H1 -> 1; 
            case H4 -> 4; // ★H4を追加
            case D1 -> 1;
            case W1 -> 7; 
            default -> 1;
        };
    }
}