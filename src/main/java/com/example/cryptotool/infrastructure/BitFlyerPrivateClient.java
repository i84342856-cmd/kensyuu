package com.example.cryptotool.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cryptotool.model.OrderRequest;

import tools.jackson.databind.ObjectMapper;

@Component
public class BitFlyerPrivateClient {

    @Value("${bitflyer.api.key}")
    private String apiKey;

    @Value("${bitflyer.api.secret}")
    private String apiSecret;

    private final WebClient webClient = WebClient.create("https://api.bitflyer.com");
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 新規注文を出すメソッド
     */
    public String sendOrder(OrderRequest order) {
        try {
            String method = "POST";
            String path = "/v1/me/sendchildorder";
            String body = objectMapper.writeValueAsString(order);
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

            // 署名の作成: ACCESS-TIMESTAMP + HTTP-METHOD + HTTP-PATH + REQUEST-BODY
            String text = timestamp + method + path + body;
            String signature = makeSignature(text, apiSecret);

            return webClient.post()
                    .uri(path)
                    .header("ACCESS-KEY", apiKey)
                    .header("ACCESS-TIMESTAMP", timestamp)
                    .header("ACCESS-SIGN", signature)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            return "注文エラー: " + e.getMessage();
        }
    }

    private String makeSignature(String text, String secret) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}