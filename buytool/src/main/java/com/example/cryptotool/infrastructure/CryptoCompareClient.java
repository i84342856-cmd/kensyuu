package com.example.cryptotool.infrastructure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Slf4j
@Component
public class CryptoCompareClient {

    private final WebClient webClient = WebClient.create("https://min-api.cryptocompare.com/data/v2");

    public List<CandleData> getHistoricalCandles(Symbol symbol, TimeFrame timeFrame, int limit) {
        // 通貨ペアからベース通貨(例: DOGE_JPY -> DOGE, FX_BTC_JPY -> BTC)を抽出
        String fsym = symbol.name().replace("FX_", "").split("_")[0];
        
        String tempEndpoint = "/histominute";
        int tempAggregate = 1;

        switch (timeFrame) {
            case M1: tempEndpoint = "/histominute"; tempAggregate = 1; break;
            case M5: tempEndpoint = "/histominute"; tempAggregate = 5; break;
            case M15: tempEndpoint = "/histominute"; tempAggregate = 15; break;
            case M30: tempEndpoint = "/histominute"; tempAggregate = 30; break;
            case H1: tempEndpoint = "/histohour"; tempAggregate = 1; break;
            case D1: tempEndpoint = "/histoday"; tempAggregate = 1; break;
            case W1: tempEndpoint = "/histoday"; tempAggregate = 7; break;
        }

        final String finalEndpoint = tempEndpoint;
        final int finalAggregate = tempAggregate;

        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path(finalEndpoint)
                            .queryParam("fsym", fsym)
                            .queryParam("tsym", "JPY")
                            .queryParam("limit", limit)
                            .queryParam("aggregate", finalAggregate)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            List<CandleData> candles = new ArrayList<>();
            if (response != null && response.has("Data") && response.get("Data").has("Data")) {
                for (JsonNode node : response.get("Data").get("Data")) {
                    candles.add(CandleData.builder()
                            .time(node.get("time").asLong())
                            .open(node.get("open").asDouble())
                            .high(node.get("high").asDouble())
                            .low(node.get("low").asDouble())
                            .close(node.get("close").asDouble())
                            .volume(node.get("volumefrom").asDouble())
                            .build());
                }
            }
            return candles;
        } catch (Exception e) {
            // 万が一データが取れなかった場合はコンソールに赤い文字で詳細な原因を出力します
            log.error("❌ [CryptoCompare API Error] {} {}足の過去データ取得に失敗: {}", fsym, timeFrame, e.getMessage());
            return new ArrayList<>();
        }
    }
}