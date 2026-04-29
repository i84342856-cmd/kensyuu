package com.example.cryptotool.infrastructure;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

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
// 【修正箇所1】インターフェースをインポートする
import com.example.cryptotool.service.MarketDataService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class BitFlyerWebsocketClient extends TextWebSocketHandler implements InitializingBean {

    private static final String BITFLYER_WS_URL = "wss://ws.lightstream.bitflyer.com/json-rpc";
    
    // 【修正箇所2】インターフェースの型にする
    private final MarketDataService marketDataService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private WebSocketSession currentSession;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    // 【修正箇所3】コンストラクタもインターフェースで受け取る
    public BitFlyerWebsocketClient(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Override
    public void afterPropertiesSet() {
        connect();
    }

    /**
     * bitFlyerへの接続を開始する。
     * メンテナンス中などで接続に失敗しても、ウォッチドッグが自動で再試行を繰り返す。
     */
    private synchronized void connect() {
        if (currentSession != null && currentSession.isOpen()) {
            return;
        }

        if (!isConnecting.compareAndSet(false, true)) {
            return;
        }

        log.info("🔌 bitFlyer WebSocketに接続を試みます...");
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            WebSocketConnectionManager manager = new WebSocketConnectionManager(client, this, BITFLYER_WS_URL);
            manager.setAutoStartup(true);
            manager.start();

            scheduler.schedule(() -> {
                if (currentSession == null || !currentSession.isOpen()) {
                    log.warn("⚠️ 30秒経過しても接続が確立されません。再接続ステータスをリセットします。");
                    isConnecting.set(false);
                    scheduleReconnect();
                }
            }, 30, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("❌ WebSocketマネージャーの起動中に例外が発生しました", e);
            isConnecting.set(false);
            scheduleReconnect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.currentSession = session;
        this.isConnecting.set(false); 
        
        log.info("🌐 bitFlyer WebSocket接続完了！全通貨のリアルタイム購読を開始します...");
        session.setTextMessageSizeLimit(1024 * 1024);

        // 手動のリスト(validPairs)を廃止し、すべてのSymbolを対象にする
        for (Symbol symbol : Symbol.values()) {
            String channelName = "lightning_executions_" + symbol.name();
            String subscribeMessage = String.format(
                    "{\"jsonrpc\":\"2.0\",\"method\":\"subscribe\",\"id\":1,\"params\":{\"channel\":\"%s\"}}", channelName);
            session.sendMessage(new TextMessage(subscribeMessage));
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
                
                marketDataService.processRealtimeTick(tick);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        this.currentSession = null;
        this.isConnecting.set(false);
        log.warn("❌ bitFlyer WebSocketが切断されました。理由: {}", status);
        scheduleReconnect();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("⚠️ WebSocket通信エラーが発生しました", exception);
        if (session.isOpen()) {
            session.close();
        }
    }

    private void scheduleReconnect() {
        if (isConnecting.get()) return; 

        log.info("🔄 30秒後に再接続を試みます...");
        scheduler.schedule(this::connect, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        if (!scheduler.isShutdown()) {
            log.info("🛑 WebSocketスケジューラーを停止します...");
            scheduler.shutdownNow();
        }
    }
}