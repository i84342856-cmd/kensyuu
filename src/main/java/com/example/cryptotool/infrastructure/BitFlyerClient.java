package com.example.cryptotool.infrastructure;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cryptotool.model.enums.Symbol;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BitFlyerClient {
    private final WebClient webClient = WebClient.create("https://api.bitflyer.com/v1");

    /**
     * 最新の板情報（Mid Price取得用）や過去の約定データを取得
     * ※bitFlyerのPublic APIは制限が緩いですが、頻繁なアクセスには注意が必要です。
     */
    public double getMidPrice(Symbol symbol) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/getboard")
                        .queryParam("product_code", symbol.name())
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return (double) response.get("mid_price");
    }

    // 本来はここから過去の約定(executions)を取得し、ローソク足を生成します
}