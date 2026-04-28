package com.example.cryptotool.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class MlPredictionClient {
	private final WebClient webClient = WebClient.create("http://162.43.93.111:8000");

    // XGBoostによる上昇確率の推論
    public double getPredictionProbability(double[] features) {
        try {
            return webClient.post()
                    .uri("/predict/xgboost")
                    .bodyValue(features)
                    .retrieve()
                    .bodyToMono(Double.class)
                    .block();
        } catch (Exception e) {
            return 0.5; // エラー時は中立を返す
        }
    }

    // HMMによる市場レジーム判定
    public int getHmmRegime(double[] observations) {
        try {
            return webClient.post()
                    .uri("/predict/hmm")
                    .bodyValue(observations)
                    .retrieve()
                    .bodyToMono(Integer.class)
                    .block();
        } catch (Exception e) {
            return -1; // 不明なレジーム
        }
    }
}