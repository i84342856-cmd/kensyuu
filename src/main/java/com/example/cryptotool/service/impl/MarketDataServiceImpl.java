package com.example.cryptotool.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.infrastructure.BitFlyerClient;
import com.example.cryptotool.infrastructure.CryptoCompareClient;
import com.example.cryptotool.infrastructure.MlPredictionClient;
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
import com.example.cryptotool.service.core.MarketRegimeService;
import com.example.cryptotool.service.strategy.TradingStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CryptoCompareClient cryptoCompareClient;
    private final BitFlyerClient bitFlyerClient; 
    private final TradeLogRepository tradeLogRepository;

    // ★新アーキテクチャの要：データストアと戦略リスト
    private final MarketDataStore dataStore;
    private final List<TradingStrategy> strategies;
    
    private final MlPredictionClient mlClient;
    private final MarketRegimeService regimeService;

    private final Map<MarketKey, Long> lastOrderTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> monitorSettings = new ConcurrentHashMap<>();

    // ポジション管理
    private final Map<MarketKey, String> positionMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Double> entryPriceMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Double> positionSizeMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Integer> entryStrategyMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, Long> entryCandleTimeMap = new ConcurrentHashMap<>();

    private boolean isSystemReady = false;
    private long lastTickReceivedTime = System.currentTimeMillis();

    private final double TARGET_TRADE_AMOUNT = 400000.0;

    private final Map<String, Boolean> strategySettings = new ConcurrentHashMap<>();

    private boolean isTargetSymbol(Symbol s) { 
        // bitFlyer Lightning（取引所）でリアルタイムデータが配信されている銘柄に限定
        return s == Symbol.BTC_JPY || 
               s == Symbol.FX_BTC_JPY || 
               s == Symbol.ETH_JPY || 
               s == Symbol.XRP_JPY || 
               s == Symbol.LTC_JPY || 
               s == Symbol.BCH_JPY || 
               s == Symbol.MONA_JPY;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("🚀 システム起動: 非同期初期化を開始します...");
        addSystemLog("SYSTEM BOOTING", "システム初期化中...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                for (Symbol s : Symbol.values()) {
                    if (!isTargetSymbol(s)) continue;
                    for (TimeFrame tf : TimeFrame.values()) {
                        if (tf == TimeFrame.M1) continue; 
                        
                        MarketKey key = new MarketKey(s, tf);
                        List<CandleData> fetched = null;
                        
                        try {
                        	// 起動時の初期データは50本だけ取得してAPI制限を回避する
                        	fetched = cryptoCompareClient.getHistoricalCandles(s, tf, 50);
                        } catch (Exception e) {
                            log.warn("履歴データ取得APIエラー ({}): {}", key, e.getMessage());
                        }
                        
                        if (fetched == null || fetched.isEmpty()) {
                            fetched = generateFallbackCandles(s, tf);
                        }

                        if (!fetched.isEmpty()) {
                            CandleData last = fetched.remove(fetched.size() - 1);
                            dataStore.getCurrentCandleMap().put(key, last);
                        }
                        dataStore.getHistoryMap().put(key, fetched);

                        // =========================================================
                        // ★ポジション復元処理（アプリ再起動時にもここから引き継ぐ）
                        // =========================================================
                        Optional<TradeLog> optLatestLog = tradeLogRepository.findFirstBySymbolAndTimeframeOrderByTimeDesc(s.name(), tf.name());
                        boolean hasPosition = false;
                        if (optLatestLog.isPresent()) {
                            TradeLog latestLog = optLatestLog.get();
                            if (!latestLog.getMessage().contains("決済") && !latestLog.getMessage().contains("SYSTEM")) {
                                String restoredPos = latestLog.getMessage().contains("[LONG]") ? "LONG" : "SHORT";
                                positionMap.put(key, restoredPos);
                                entryPriceMap.put(key, latestLog.getPrice());
                                positionSizeMap.put(key, latestLog.getSize());
                                entryStrategyMap.put(key, latestLog.getStrategy());
                                long candleStart = (latestLog.getTime() / tf.getSeconds()) * tf.getSeconds();
                                entryCandleTimeMap.put(key, candleStart);
                                hasPosition = true;
                                log.info("♻️ 過去のポジションを復元: {} {} (戦略:{}, Price:{})", key, restoredPos, latestLog.getStrategy(), latestLog.getPrice());
                            }
                        }
                        if (!hasPosition) {
                            positionMap.put(key, "NONE");
                            entryPriceMap.put(key, 0.0);
                            positionSizeMap.put(key, 0.0);
                            entryStrategyMap.put(key, 0);
                            entryCandleTimeMap.put(key, 0L);
                        }
                     // 修正後
                        lastOrderTimeMap.put(key, 0L);
                        monitorSettings.put(key.toString(), true);
                        // 2000ms（2秒）がAPI制限を回避するための安全圏です
                        Thread.sleep(2000);
                    }
                }
                isSystemReady = true;
                addSystemLog("SYSTEM READY", "全データの準備が完了しました。");
            } catch (Exception e) {
                log.error("初期化エラー", e);
            }
        });
    }

    private List<CandleData> generateFallbackCandles(Symbol symbol, TimeFrame tf) {
        List<CandleData> initialCandles = new ArrayList<>();
        double basePrice = 5000000; 
        long currentPeriod = (System.currentTimeMillis() / 1000 / tf.getSeconds()) * tf.getSeconds();
        double lastClose = basePrice;
        for (int i = 300; i >= 0; i--) { 
            long time = currentPeriod - ((long) i * tf.getSeconds());
            double open = lastClose; 
            double close = open * (1.0 + (Math.random() - 0.5) * 0.001);
            double high = Math.max(open, close) * (1.0 + Math.random() * 0.0005);
            double low = Math.min(open, close) * (1.0 - Math.random() * 0.0005); 
            initialCandles.add(ChartInitResponse.CandleData.builder()
                    .time(time).open(open).high(high).low(low).close(close).volume(100.0).build());
            lastClose = close;
        }
        return initialCandles;
    }

    public void updateMonitorSetting(String symbol, String timeframe, boolean active) { monitorSettings.put(symbol + "_" + timeframe, active); }
    public Map<String, Boolean> getMonitorSettings() { return monitorSettings; }
    public void updateStrategySetting(String id, boolean active) { strategySettings.put(id, active); }
    public Map<String, Boolean> getStrategySettings() { return strategySettings; }
    public List<TradeLog> getTradeHistory() { return tradeLogRepository.findTop100ByOrderByTimeDesc(); }
    @Override public List<TradeLog> getAllTradeHistory() { return tradeLogRepository.findAllByOrderByTimeDesc(); }
    @Override public List<TradeLog> getTradeLogsForChart(Symbol symbol, TimeFrame tf) { return tradeLogRepository.findAllBySymbolAndTimeframeOrderByTimeAsc(symbol.name(), tf.name()); }

    @Override
    public ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame) {
        MarketKey key = new MarketKey(symbol, timeFrame);
        List<CandleData> candles = new ArrayList<>(dataStore.getHistoryMap().getOrDefault(key, new ArrayList<>()));
        CandleData current = dataStore.getCurrentCandleMap().get(key);
        if (current != null) candles.add(current);
        return ChartInitResponse.builder().candles(candles)
                .ma5(calculateHistoricalMA(candles, 5)).ma10(calculateHistoricalMA(candles, 10))
                .ma25(calculateHistoricalMA(candles, 25)).ma50(calculateHistoricalMA(candles, 50))
                .ma75(calculateHistoricalMA(candles, 75)).ma100(calculateHistoricalMA(candles, 100)).build();
    }

    @Override
    public void processRealtimeTick(TickData tick) {
        this.lastTickReceivedTime = System.currentTimeMillis();
        if (!isSystemReady || !isTargetSymbol(tick.getSymbol())) return;
        for (TimeFrame tf : TimeFrame.values()) {
            if (tf == TimeFrame.M1) continue; 
            updateAndCheckSignal(tick, tf);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void watchdogTimer() {
        if (System.currentTimeMillis() - lastTickReceivedTime > 300000) {
            log.error("🚨 5分間データ未受信。強制再接続します...");
            lastTickReceivedTime = System.currentTimeMillis();
        }
    }

    private void updateAndCheckSignal(TickData tick, TimeFrame tf) {
        Symbol symbol = tick.getSymbol();
        MarketKey key = new MarketKey(symbol, tf);
        long candleStart = (tick.getTimestamp() / tf.getSeconds()) * tf.getSeconds();
        double price = tick.getPrice();

        CandleData current = dataStore.getCurrentCandleMap().get(key);
        if (current == null || current.getTime() < candleStart) {
            if (current != null) dataStore.getHistoryMap().get(key).add(current);
            current = CandleData.builder().time(candleStart).open(price).high(price).low(price).close(price).build();
            dataStore.getCurrentCandleMap().put(key, current);
        } else {
            current.setClose(price);
            current.setHigh(Math.max(current.getHigh(), price));
            current.setLow(Math.min(current.getLow(), price));
        }

        SignalDecision decision = null;
        if (monitorSettings.getOrDefault(key.toString(), false)) {
            decision = checkSignal(key, current);
            if (decision != null && decision.type() != RealtimeUpdateDto.SignalType.NONE) {
                executeTrade(key, decision, current);
            }
        }

     // --- ▼ 画面表示用にAI推論結果を取得 ▼ ---
        double aiProb = 0.5;
        String regimeName = "UNKNOWN";
        try {
            double rsi = dataStore.getRSI(key, 14, 0);
            double stdDev = dataStore.getStdDev(key, 20, 0);
            double maDev = dataStore.getMaDeviationRate(key, 20, 0);
            
            double ma5 = dataStore.getPastMA(key, 5, 0);
            double ma25 = dataStore.getPastMA(key, 25, 0);
            double f4_macd = (ma25 != 0) ? ((ma5 - ma25) / ma25) * 100 : 0; 
            
            double open = current.getOpen();
            double close = current.getClose();
            double high = current.getHigh();
            double low = current.getLow();
            double f5_body = (close != 0) ? (Math.abs(close - open) / close) * 100 : 0; 
            double f6_upper = (close != 0) ? ((high - Math.max(open, close)) / close) * 100 : 0; 
            double f7_lower = (close != 0) ? ((Math.min(open, close) - low) / close) * 100 : 0; 
            
            // 💡【工夫1】StdDevを価格に対するパーセント（ボラティリティ率）に変換
            double stdDevPct = (close != 0) ? (stdDev / close) * 100 : 0;

            // 💡【工夫2】Volumeを1本前の足との比率（出来高変化率）に変換
            double prevVol = 0;
            java.util.List<CandleData> history = dataStore.getHistoryMap().get(key);
            if (history != null && !history.isEmpty()) {
                prevVol = history.get(history.size() - 1).getVolume();
            }
            double volRatio = (prevVol != 0) ? (current.getVolume() / prevVol) * 100 : 100;
            
            // Pythonへデータを送信（すべてが普遍的なパーセント指標になりました）
            double[] features = new double[]{ rsi, stdDevPct, maDev, f4_macd, f5_body, f6_upper, f7_lower, volRatio, 0 };
            
            aiProb = mlClient.getPredictionProbability(features);
            regimeName = regimeService.detectRegime(key, dataStore).name();
        } catch (Exception e) {
            log.warn("画面表示用のAI推論でエラー: {}", e.getMessage());
        }
        // --- ▲ ここまで ▲ ---
        
        // WebSocketでフロントエンドにデータを送信
        messagingTemplate.convertAndSend("/topic/" + key.toString(), RealtimeUpdateDto.builder()
                .currentCandle(current)
                .currentMa5(dataStore.getPastMA(key, 5, 0))
                .currentMa25(dataStore.getPastMA(key, 25, 0))
                .signal(decision != null ? decision.type() : RealtimeUpdateDto.SignalType.NONE)
                .aiUpProbability(aiProb)           // ★追加：AIの予測確率
                .marketRegime(regimeName)          // ★追加：HMMのレジーム
                .build());
    }

    private SignalDecision checkSignal(MarketKey key, CandleData current) {
        String currentPosition = positionMap.getOrDefault(key, "NONE");

        // ★全戦略を自動評価するダイナミックループ
        for (TradingStrategy strategy : strategies) {
            if (!strategySettings.getOrDefault(String.valueOf(strategy.getStrategyId()), true)) continue;

            if ("NONE".equals(currentPosition)) {
                SignalDecision decision = strategy.checkEntry(key, current, dataStore);
                if (decision != null) return decision;
            } else {
                int entryStrat = entryStrategyMap.getOrDefault(key, 0);
                // 自分がエントリーした戦略系統（例:501なら500）の時だけ決済を評価する
                if (entryStrat == strategy.getStrategyId() || (entryStrat / 100 * 100) == strategy.getStrategyId()) {
                    double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
                    long entryTime = entryCandleTimeMap.getOrDefault(key, 0L);
                    SignalDecision decision = strategy.checkExit(key, current, dataStore, currentPosition, entryPrice, entryTime);
                    if (decision != null) return decision;
                }
            }
        }
        return null;
    }

    private void executeTrade(MarketKey key, SignalDecision decision, CandleData candle) {
        String currentPos = positionMap.getOrDefault(key, "NONE");
        boolean isNewEntry = "NONE".equals(currentPos);
        if (isNewEntry && lastOrderTimeMap.getOrDefault(key, 0L).equals(candle.getTime())) return;

        String newPos = currentPos; String actionType = ""; double tradeSize = 0.001;
        if (isNewEntry) {
            tradeSize = Math.round((TARGET_TRADE_AMOUNT / candle.getClose()) * 10000.0) / 10000.0;
            newPos = decision.type() == RealtimeUpdateDto.SignalType.BUY ? "LONG" : "SHORT";
            actionType = (newPos.equals("LONG") ? "🟢 [LONG] " : "🔴 [SHORT] ") + decision.reason();
        } else {
            tradeSize = positionSizeMap.getOrDefault(key, 0.001);
            if ("LONG".equals(currentPos) && decision.type() == RealtimeUpdateDto.SignalType.SELL) { newPos = "NONE"; actionType = "✅ [LONG決済] " + decision.reason(); }
            else if ("SHORT".equals(currentPos) && decision.type() == RealtimeUpdateDto.SignalType.BUY) { newPos = "NONE"; actionType = "✅ [SHORT決済] " + decision.reason(); }
            else return;
        }

        TradeLog logTrade = new TradeLog();
        logTrade.setTime(System.currentTimeMillis() / 1000); logTrade.setSymbol(key.symbol().name()); logTrade.setTimeframe(key.timeFrame().name());
        logTrade.setSide(decision.type().name()); logTrade.setPrice(candle.getClose()); logTrade.setSize(tradeSize);
        logTrade.setMessage(actionType); logTrade.setStrategy(decision.strategyId());

        tradeLogRepository.save(logTrade);
        messagingTemplate.convertAndSend("/topic/trades", logTrade);

        lastOrderTimeMap.put(key, candle.getTime()); positionMap.put(key, newPos);
        if (!"NONE".equals(newPos)) {
            entryPriceMap.put(key, candle.getClose()); positionSizeMap.put(key, tradeSize);
            entryStrategyMap.put(key, decision.strategyId()); entryCandleTimeMap.put(key, candle.getTime());
        } else {
            entryPriceMap.remove(key); positionSizeMap.remove(key);
            entryStrategyMap.remove(key); entryCandleTimeMap.remove(key);
        }
    }

    private List<ChartInitResponse.MovingAverageData> calculateHistoricalMA(List<CandleData> candles, int period) {
        List<ChartInitResponse.MovingAverageData> res = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0; for (int j = 0; j < period; j++) sum += candles.get(i - j).getClose();
            res.add(new ChartInitResponse.MovingAverageData(candles.get(i).getTime(), sum / period));
        }
        return res;
    }

    private void addSystemLog(String status, String message) {
        TradeLog systemLog = new TradeLog();
        systemLog.setTime(System.currentTimeMillis() / 1000); systemLog.setSymbol("SYSTEM"); systemLog.setTimeframe("-");
        systemLog.setSide(status); systemLog.setPrice(0.0); systemLog.setSize(0.0);
        systemLog.setMessage(message); systemLog.setStrategy(0);
        tradeLogRepository.save(systemLog); messagingTemplate.convertAndSend("/topic/trades", systemLog);
    }
}