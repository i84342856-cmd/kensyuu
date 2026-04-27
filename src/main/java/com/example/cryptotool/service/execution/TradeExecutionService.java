package com.example.cryptotool.service.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto.SignalType;
import com.example.cryptotool.repository.TradeLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 注文の実行、DBへの保存、ポジション情報の管理を専門に行うクラス
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeExecutionService {

    private final TradeLogRepository tradeLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // Stringキーを排除し、MarketKeyで厳格にポジションを管理
    private final Map<MarketKey, String> positionMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Double> entryPriceMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Double> positionSizeMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Long> entryCandleTimeMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Long> lastOrderTimeMap = new ConcurrentHashMap<>();

    private final double TARGET_TRADE_AMOUNT = 400000.0;

    public void initializePosition(MarketKey key, String position, double price, double size, long entryTime) {
        positionMap.put(key, position);
        entryPriceMap.put(key, price);
        positionSizeMap.put(key, size);
        entryCandleTimeMap.put(key, entryTime);
        lastOrderTimeMap.put(key, 0L);
    }

    public String getCurrentPosition(MarketKey key) {
        return positionMap.getOrDefault(key, "NONE");
    }

    public double getEntryPrice(MarketKey key) {
        return entryPriceMap.getOrDefault(key, 0.0);
    }

    public long getEntryTime(MarketKey key) {
        return entryCandleTimeMap.getOrDefault(key, 0L);
    }

    public void executeTrade(MarketKey key, SignalDecision decision, CandleData candle) {
        String currentPos = getCurrentPosition(key);
        boolean isNewEntry = "NONE".equals(currentPos);

        // 同一足での連続エントリー防止
        if (isNewEntry && lastOrderTimeMap.getOrDefault(key, 0L).equals(candle.getTime())) {
            return;
        }

        String newPos = currentPos;
        String actionType = "";
        double tradeSize = 0.001;

        if (isNewEntry) {
            tradeSize = calculateLotSize(candle.getClose());
            if (decision.type() == SignalType.BUY) {
                newPos = "LONG"; 
                actionType = "🟢 [LONG] " + decision.reason();
            } else {
                newPos = "SHORT"; 
                actionType = "🔴 [SHORT] " + decision.reason();
            }
        } else {
            tradeSize = positionSizeMap.getOrDefault(key, 0.001);
            if ("LONG".equals(currentPos) && decision.type() == SignalType.SELL) {
                newPos = "NONE"; 
                actionType = "✅ [LONG決済] " + decision.reason();
            } else if ("SHORT".equals(currentPos) && decision.type() == SignalType.BUY) {
                newPos = "NONE"; 
                actionType = "✅ [SHORT決済] " + decision.reason();
            } else {
                return; // 不正なシグナルは無視
            }
        }

        // DBへの記録とWebSocket通知
        TradeLog logTrade = new TradeLog();
        logTrade.setTime(System.currentTimeMillis() / 1000);
        logTrade.setSymbol(key.symbol().name());
        logTrade.setTimeframe(key.timeFrame().name());
        logTrade.setSide(decision.type().name());
        logTrade.setPrice(candle.getClose());
        logTrade.setSize(tradeSize);
        logTrade.setMessage(actionType);
        logTrade.setStrategy(decision.strategyId());

        tradeLogRepository.save(logTrade);
        messagingTemplate.convertAndSend("/topic/trades", logTrade);

        // ポジション状態の更新
        lastOrderTimeMap.put(key, candle.getTime());
        positionMap.put(key, newPos);

        if (!"NONE".equals(newPos)) {
            entryPriceMap.put(key, candle.getClose());
            positionSizeMap.put(key, tradeSize);
            entryCandleTimeMap.put(key, candle.getTime());
            log.info("🚀 新規ポジション作成: {} {} 価格: {}", key, newPos, candle.getClose());
        } else {
            entryPriceMap.remove(key);
            positionSizeMap.remove(key);
            entryCandleTimeMap.remove(key);
            log.info("🏁 ポジション決済完了: {}", key);
        }
    }

    private double calculateLotSize(double price) {
        if (price <= 0) return 0.001;
        double size = TARGET_TRADE_AMOUNT / price;
        return Math.round(size * 10000.0) / 10000.0;
    }
}