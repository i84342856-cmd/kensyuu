package com.example.cryptotool.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import jakarta.annotation.PostConstruct;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.cryptotool.infrastructure.BitFlyerPrivateClient;
import com.example.cryptotool.infrastructure.CryptoCompareClient;
import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto;
import com.example.cryptotool.service.MarketDataService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private final SimpMessagingTemplate messagingTemplate;
    private final CryptoCompareClient cryptoCompareClient;
    private final BitFlyerPrivateClient bitFlyerPrivateClient;

    // キー: "BTC_JPY_M5" のような形式で全通貨・全時間足をO(1)で超高速検索
    private final Map<String, List<CandleData>> historyMap = new ConcurrentHashMap<>();
    private final Map<String, CandleData> currentCandleMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOrderTimeMap = new ConcurrentHashMap<>();
    private final Map<String, String> positionMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> monitorSettings = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> tradeHistoryList = new CopyOnWriteArrayList<>();

    private final boolean IS_DEMO_MODE = true; 
    private boolean isSystemReady = false; // データ準備完了フラグ

    @PostConstruct
    public void init() {
        log.info("🚀 システム起動: 全通貨・全時間足の非同期初期化を開始します...");
        addSystemLog("SYSTEM BOOTING", "システム初期化中...");

        // API制限（Rate Limit）を回避するため、別スレッドで少しずつデータを取得する最適化構造
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                for (Symbol s : Symbol.values()) {
                    for (TimeFrame tf : TimeFrame.values()) {
                        String key = s.name() + "_" + tf.name();
                        List<CandleData> fetched = cryptoCompareClient.getHistoricalCandles(s, tf, 1000);
                        historyMap.put(key, fetched);
                        if (!fetched.isEmpty()) currentCandleMap.put(key, fetched.get(fetched.size() - 1));
                        
                        positionMap.put(key, "NONE");
                        lastOrderTimeMap.put(key, 0L);
                        monitorSettings.put(key, false); // 初期状態は自動売買OFF
                        
                        Thread.sleep(150); // ★150ミリ秒待機してAPIのBANを完全に防ぐ
                    }
                }
                isSystemReady = true;
                addSystemLog("SYSTEM READY", "全データの準備が完了し、全時間足の監視態勢に入りました。");
                log.info("✅ 初期化完了: すべての時間足のデータを安全にロードしました。");
            } catch (Exception e) {
                log.error("初期化中にエラーが発生しました", e);
            }
        });
    }

    // --- 設定・履歴取得API群 ---
 // 監視設定を更新するAPI用メソッド
    public void updateMonitorSetting(String symbol, String timeframe, boolean active) {
        String key = symbol + "_" + timeframe;
        monitorSettings.put(key, active);
        log.info("設定変更: {} -> 監視{}", key, active ? "ON" : "OFF");
    }

    // ★追加：全通貨の指定時間足の監視を一括でONにする
    public void enableAllForTimeframe(String timeframe) {
        for (Symbol s : Symbol.values()) {
            String key = s.name() + "_" + timeframe;
            monitorSettings.put(key, true);
        }
        log.info("🔥 全20通貨の {} 監視を一括でONにしました！", timeframe);
        addSystemLog("SYSTEM INFO", "全通貨の " + timeframe + " 自動売買監視を一括で開始しました。");
    }
    
    public Map<String, Boolean> getMonitorSettings() { return monitorSettings; }
    public List<Map<String, Object>> getTradeHistory() { return tradeHistoryList; }

    @Override
    public ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame) {
        String key = symbol.name() + "_" + timeFrame.name();
        List<CandleData> candles = new ArrayList<>(historyMap.getOrDefault(key, new ArrayList<>()));
        return ChartInitResponse.builder().candles(candles)
                .ma5(calculateHistoricalMA(candles, 5)).ma10(calculateHistoricalMA(candles, 10))
                .ma25(calculateHistoricalMA(candles, 25)).ma50(calculateHistoricalMA(candles, 50)).ma100(calculateHistoricalMA(candles, 100)).build();
    }

    // --- コアエンジン: マルチタイムフレーム・アグリゲーター ---
    @Override
    public void processRealtimeTick(TickData tick) {
        if (!isSystemReady) return; // 準備中は何もしない
        
        // 1つのTick(価格)から、全時間足(M1, M5, M15, H1)のローソク足を同時に生成・更新する
        for (TimeFrame tf : TimeFrame.values()) {
            updateAndCheckSignal(tick, tf);
        }
    }

    private void updateAndCheckSignal(TickData tick, TimeFrame tf) {
        String key = tick.getSymbol().name() + "_" + tf.name();
        long candleStart = (tick.getTimestamp() / tf.getSeconds()) * tf.getSeconds();
        double price = tick.getPrice();

        CandleData current = currentCandleMap.get(key);
        if (current == null || current.getTime() < candleStart) {
            if (current != null) historyMap.get(key).add(current);
            current = CandleData.builder().time(candleStart).open(price).high(price).low(price).close(price).build();
            currentCandleMap.put(key, current);
        } else {
            current.setClose(price);
            current.setHigh(Math.max(current.getHigh(), price));
            current.setLow(Math.min(current.getLow(), price));
        }

        double ma5 = calculateCurrentMA(key, 5);
        
        // --- 拡張可能なシグナル判定エンジンへ ---
        RealtimeUpdateDto.SignalType signal = checkSignal(key, current, ma5);

        // ブラウザ画面で「ON」になっている時間足のみトレードを実行
        if (signal != RealtimeUpdateDto.SignalType.NONE && monitorSettings.getOrDefault(key, false)) {
            executeTrade(tick.getSymbol(), tf, signal, current);
        }

        // 画面のチャートを更新
        messagingTemplate.convertAndSend("/topic/" + key, 
            RealtimeUpdateDto.builder().currentCandle(current).currentMa5(ma5).signal(signal).build());
    }

    /**
     * 🧠 【拡張用】シグナル判定エンジン
     * 今後、ここに超詳細な要件（複数インジケーターの組み合わせ等）を追加していきます。
     */
    private RealtimeUpdateDto.SignalType checkSignal(String key, CandleData c, double ma5) {
        if (ma5 == 0 || c == null) return RealtimeUpdateDto.SignalType.NONE;

        // 【現状の暫定ロジック】
        double mid = (c.getOpen() + c.getClose()) / 2.0;
        if (c.getClose() > c.getOpen() && c.getOpen() <= ma5 && mid > ma5) return RealtimeUpdateDto.SignalType.BUY;
        if (c.getOpen() > c.getClose() && c.getOpen() >= ma5 && mid < ma5) return RealtimeUpdateDto.SignalType.SELL;
        
        return RealtimeUpdateDto.SignalType.NONE;
    }

    private void executeTrade(Symbol symbol, TimeFrame tf, RealtimeUpdateDto.SignalType signal, CandleData candle) {
        String key = symbol.name() + "_" + tf.name();
        if (lastOrderTimeMap.get(key).equals(candle.getTime())) return;
        if (signal.name().equals(positionMap.get(key))) return;

        Map<String, Object> logTrade = new HashMap<>();
        logTrade.put("time", System.currentTimeMillis() / 1000);
        logTrade.put("symbol", symbol.name());
        logTrade.put("timeframe", tf.name()); // どの時間足で約定したか記録
        logTrade.put("side", signal.name());
        logTrade.put("price", candle.getClose());
        logTrade.put("size", 0.001);
        
        tradeHistoryList.add(0, logTrade);
        Object payload = logTrade; 
        messagingTemplate.convertAndSend("/topic/trades", payload);

        lastOrderTimeMap.put(key, candle.getTime());
        positionMap.put(key, signal.name());
        log.info("★★★ [自動売買] [{}] {} 注文を実行しました。価格: {} ★★★", key, signal, candle.getClose());
    }

    // --- 計算ユーティリティ ---
    private double calculateCurrentMA(String key, int period) {
        List<CandleData> hist = historyMap.get(key);
        CandleData cur = currentCandleMap.get(key);
        if (hist == null || hist.size() < period - 1) return 0;
        double sum = cur.getClose();
        for (int i = 1; i < period; i++) sum += hist.get(hist.size() - i).getClose();
        return sum / period;
    }

    private List<ChartInitResponse.MovingAverageData> calculateHistoricalMA(List<CandleData> candles, int period) {
        List<ChartInitResponse.MovingAverageData> res = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) sum += candles.get(i - j).getClose();
            res.add(new ChartInitResponse.MovingAverageData(candles.get(i).getTime(), sum / period));
        }
        return res;
    }

    private void addSystemLog(String status, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("time", System.currentTimeMillis() / 1000); 
        m.put("symbol", "SYSTEM");
        m.put("timeframe", "-"); 
        m.put("side", status); 
        m.put("price", 0.0); 
        m.put("size", 0.0);
        m.put("message", message);
        tradeHistoryList.add(0, m);
        Object payload = m;
        messagingTemplate.convertAndSend("/topic/trades", payload);
    }
}