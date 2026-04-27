package com.example.cryptotool.service.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto;
import com.example.cryptotool.repository.TradeLogRepository;
import com.example.cryptotool.service.MarketDataService;
import com.example.cryptotool.service.core.MarketDataStore;
import com.example.cryptotool.service.execution.TradeExecutionService;
import com.example.cryptotool.service.strategy.TradingStrategy;

/**
 * 【司令塔】データ受信と各サービスへの指示出しのみを担当
 */
@Service
public class MarketDataServiceImpl implements MarketDataService {

    // @Slf4j の代わりに手動でロガーを定義（IDEの自動インポート暴走を防止）
    private static final Logger log = LoggerFactory.getLogger(MarketDataServiceImpl.class);

    private final MarketDataStore dataStore;
    private final TradeExecutionService executionService;
    private final List<TradingStrategy> strategies;
    private final TradeLogRepository tradeLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<MarketKey, Boolean> monitorSettings = new ConcurrentHashMap<>();
    private final Map<String, Boolean> strategySettings = new ConcurrentHashMap<>();

    // @RequiredArgsConstructor の代わりに手動でコンストラクタを定義（初期化エラーを防止）
    public MarketDataServiceImpl(
            MarketDataStore dataStore,
            TradeExecutionService executionService,
            List<TradingStrategy> strategies,
            TradeLogRepository tradeLogRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.dataStore = dataStore;
        this.executionService = executionService;
        this.strategies = strategies;
        this.tradeLogRepository = tradeLogRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void processRealtimeTick(TickData tick) {
        if (!dataStore.isReady()) return;

        for (TimeFrame tf : TimeFrame.values()) {
            if (tf == TimeFrame.M1) continue; 

            MarketKey key = new MarketKey(tick.getSymbol(), tf);
            
            CandleData current = dataStore.updateCandle(tick, tf);
            if (current == null) continue;

            if (monitorSettings.getOrDefault(key, false)) {
                checkStrategiesAndExecute(key, current);
            }

            sendChartUpdate(key, current);
        }
    }

    private void checkStrategiesAndExecute(MarketKey key, CandleData current) {
        String currentPosition = executionService.getCurrentPosition(key);
        double entryPrice = executionService.getEntryPrice(key);
        long entryTime = executionService.getEntryTime(key);

        for (TradingStrategy strategy : strategies) {
            if (!strategySettings.getOrDefault(String.valueOf(strategy.getStrategyId()), true)) {
                continue;
            }

            SignalDecision decision = null;

            if ("NONE".equals(currentPosition)) {
                decision = strategy.checkEntry(key, current, dataStore);
            } else {
                decision = strategy.checkExit(key, current, dataStore, currentPosition, entryPrice, entryTime);
            }

            if (decision != null && decision.type() != RealtimeUpdateDto.SignalType.NONE) {
                executionService.executeTrade(key, decision, current);
                break; 
            }
        }
    }

    private void sendChartUpdate(MarketKey key, CandleData current) {
        String topicStr = key.symbol().name() + "_" + key.timeFrame().name();
        messagingTemplate.convertAndSend("/topic/" + topicStr,
                RealtimeUpdateDto.builder()
                        .currentCandle(current)
                        .currentMa5(dataStore.calculateCurrentMA(key, 5))
                        .currentMa10(dataStore.calculateCurrentMA(key, 10))
                        .currentMa25(dataStore.calculateCurrentMA(key, 25))
                        .currentMa50(dataStore.calculateCurrentMA(key, 50))
                        .currentMa75(dataStore.calculateCurrentMA(key, 75))
                        .currentMa100(dataStore.calculateCurrentMA(key, 100))
                        .signal(RealtimeUpdateDto.SignalType.NONE) 
                        .build());
    }

    @Override
    public ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame) {
        MarketKey key = new MarketKey(symbol, timeFrame);
        List<CandleData> candles = dataStore.getHistoryClone(key);
        CandleData current = dataStore.getCurrentCandle(key);
        if (current != null) candles.add(current);

        return ChartInitResponse.builder()
                .candles(candles)
                .ma5(dataStore.calculateHistoricalMA(candles, 5))
                .ma10(dataStore.calculateHistoricalMA(candles, 10))
                .ma25(dataStore.calculateHistoricalMA(candles, 25))
                .ma50(dataStore.calculateHistoricalMA(candles, 50))
                .ma75(dataStore.calculateHistoricalMA(candles, 75))
                .ma100(dataStore.calculateHistoricalMA(candles, 100))
                .build();
    }

    @Override
    public void updateMonitorSetting(String symbol, String timeframe, boolean active) {
        MarketKey key = new MarketKey(Symbol.valueOf(symbol), TimeFrame.valueOf(timeframe));
        monitorSettings.put(key, active);
        log.info("設定変更: {} -> 監視{}", key, active ? "ON" : "OFF");
    }

    @Override
    public Map<String, Boolean> getMonitorSettings() {
        Map<String, Boolean> stringMap = new ConcurrentHashMap<>();
        monitorSettings.forEach((k, v) -> stringMap.put(k.symbol().name() + "_" + k.timeFrame().name(), v));
        return stringMap;
    }

    @Override
    public Map<String, Boolean> getStrategySettings() {
        return strategySettings;
    }

    @Override
    public void updateStrategySetting(String id, boolean active) {
        strategySettings.put(id, active);
        log.info("戦略設定変更: ID{} -> {}", id, active ? "ON" : "OFF");
    }

    @Override
    public List<TradeLog> getTradeHistory() {
        return tradeLogRepository.findTop100ByOrderByTimeDesc();
    }

    @Override
    public List<TradeLog> getAllTradeHistory() {
        return tradeLogRepository.findAllByOrderByTimeDesc();
    }

    @Override
    public List<TradeLog> getTradeLogsForChart(Symbol symbol, TimeFrame timeFrame) {
        return tradeLogRepository.findAllBySymbolAndTimeframeOrderByTimeAsc(symbol.name(), timeFrame.name());
    }
}