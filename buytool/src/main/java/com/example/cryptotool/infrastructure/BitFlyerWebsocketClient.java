package com.example.cryptotool.infrastructure;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.service.impl.MarketDataServiceImpl;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class BitFlyerWebsocketClient extends TextWebSocketHandler implements InitializingBean {

    private static final String BITFLYER_WS_URL = "wss://ws.lightstream.bitflyer.com/json-rpc";
    private final MarketDataServiceImpl marketDataService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BitFlyerWebsocketClient(MarketDataServiceImpl marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketConnectionManager manager = new WebSocketConnectionManager(client, this, BITFLYER_WS_URL);
            manager.setAutoStartup(true);
            manager.start();
        } catch (Exception e) {
            log.error("bitFlyer WebSocketの起動に失敗しました", e);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("🌐 bitFlyer WebSocket接続完了！リアルタイム購読を開始します...");
        session.setTextMessageSizeLimit(1024 * 1024);

        // ★超重要修正: bitFlyer Lightningがリアルタイム配信に対応している主要通貨のみに絞る
        // これ以外のマイナー通貨を要求するとエラーで強制切断され、全チャートが固まるため。
        List<String> validPairs = Arrays.asList("BTC_JPY", "FX_BTC_JPY", "ETH_JPY", "XRP_JPY", "MONA_JPY", "XLM_JPY");

        for (Symbol symbol : Symbol.values()) {
            if (validPairs.contains(symbol.name())) {
                String channelName = "lightning_executions_" + symbol.name();
                String subscribeMessage = String.format(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"subscribe\",\"id\":1,\"params\":{\"channel\":\"%s\"}}", channelName);
                session.sendMessage(new TextMessage(subscribeMessage));
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode rootNode = objectMapper.readTree(message.getPayload());
        
        if (rootNode.has("method") && "channelMessage".equals(rootNode.get("method").asText())) {
            JsonNode params = rootNode.get("params");
            String channel = params.get("channel").asText();
            String symbolName = channel.replace("lightning_executions_", "");
            
            Symbol symbol;
            try { symbol = Symbol.valueOf(symbolName); } 
            catch (Exception e) { return; }

            JsonNode messageArray = params.get("message");
            for (JsonNode execNode : messageArray) {
                double price = execNode.get("price").asDouble();
                long timestamp = Instant.parse(execNode.get("exec_date").asText()).getEpochSecond();
                TickData tick = TickData.builder().symbol(symbol).price(price).timestamp(timestamp).build();
                
                // 時間足の指定をなくし、全時間足更新エンジンへ流し込む
                marketDataService.processRealtimeTick(tick);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.warn("❌ bitFlyer WebSocketが切断されました。理由: {}", status);
    }
}