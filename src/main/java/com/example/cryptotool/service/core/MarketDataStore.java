package com.example.cryptotool.service.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.example.cryptotool.infrastructure.CryptoCompareClient;
import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.ChartInitResponse.MovingAverageData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 【聖域クラス】データの保持・自己修復・計算のみを担当
 * 外部の戦略クラス等からは一切データを書き換えられない設計。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataStore {

    private final CryptoCompareClient cryptoCompareClient;

    // Stringキーを廃止し、安全な MarketKey オブジェクトを使用
    private final Map<MarketKey, List<CandleData>> historyMap = new ConcurrentHashMap<>();
    private final Map<MarketKey, CandleData> currentCandleMap = new ConcurrentHashMap<>();
    
    // システムの準備完了フラグ
    private boolean isSystemReady = false;

    @PostConstruct
    public void init() {
        log.info("🚀 [コア基盤] ローソク足データの初期化を開始します...");
        // メインスレッドをブロックしないよう非同期で実行
        Executors.newSingleThreadExecutor().execute(this::initializeWithRetry);
    }

    /**
     * 【レジリエンス機能】APIエラーが起きても成功するまでリトライする初期化処理
     */
    private void initializeWithRetry() {
        int retryCount = 0;
        while (!isSystemReady) {
            try {
                for (Symbol s : Symbol.values()) {
                    for (TimeFrame tf : TimeFrame.values()) {
                        if (tf == TimeFrame.M1) continue; // 1分足は除外

                        MarketKey key = new MarketKey(s, tf);
                        List<CandleData> fetched = cryptoCompareClient.getHistoricalCandles(s, tf, 1000);

                        if (fetched != null && !fetched.isEmpty()) {
                            CandleData last = fetched.remove(fetched.size() - 1);
                            historyMap.put(key, fetched);
                            currentCandleMap.put(key, last);
                        } else {
                            // データが空の場合の初期化
                            historyMap.put(key, new ArrayList<>());
                        }
                        // API制限回避のウェイト
                        Thread.sleep(3000);
                    }
                }
                isSystemReady = true;
                log.info("✅ [コア基盤] 全通貨のデータ準備が完了しました。システムReady。");
            } catch (Exception e) {
                retryCount++;
                log.error("⚠️ 初期化中にエラー発生。10秒後にリトライします... (リトライ回数: {})", retryCount, e);
                try {
                    Thread.sleep(10000); // 10秒待機して再試行
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isReady() {
        return isSystemReady;
    }

    /**
     * Tickデータを受信し、ローソク足を更新。欠損があれば自己修復する。
     */
    public CandleData updateCandle(TickData tick, TimeFrame tf) {
        if (!isSystemReady) return null;

        MarketKey key = new MarketKey(tick.getSymbol(), tf);
        long currentTickCandleStart = (tick.getTimestamp() / tf.getSeconds()) * tf.getSeconds();
        double price = tick.getPrice();

        currentCandleMap.compute(key, (k, current) -> {
            if (current == null) {
                return createNewCandle(currentTickCandleStart, price);
            }

            // 【自己修復機能】もしWebSocketが切断等で時間が飛び、ローソク足が欠損（歯抜け）している場合
            if (currentTickCandleStart > current.getTime() + tf.getSeconds()) {
                log.warn("⚠️ [データ欠損検知] {} の {} 足でデータのスキップを検知。自動修復(バックフィル)を実行します。", key.symbol(), key.timeFrame());
                repairMissingCandles(key);
                return createNewCandle(currentTickCandleStart, price);
            }

            if (current.getTime() < currentTickCandleStart) {
                // 正常に次の足へ移行
                historyMap.computeIfAbsent(key, _k -> new ArrayList<>()).add(current);
                return createNewCandle(currentTickCandleStart, price);
            } else {
                // 現在の足を更新
                current.setClose(price);
                current.setHigh(Math.max(current.getHigh(), price));
                current.setLow(Math.min(current.getLow(), price));
                return current;
            }
        });

        return currentCandleMap.get(key);
    }

    private CandleData createNewCandle(long time, double price) {
        return CandleData.builder()
                .time(time)
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .build();
    }

    /**
     * 欠損した過去データをREST APIで再取得し、マップを修復する
     */
    private void repairMissingCandles(MarketKey key) {
        try {
            List<CandleData> fetched = cryptoCompareClient.getHistoricalCandles(key.symbol(), key.timeFrame(), 100);
            if (fetched != null && !fetched.isEmpty()) {
                CandleData last = fetched.remove(fetched.size() - 1);
                historyMap.put(key, fetched);
                // currentCandleMap は呼び出し元で上書きされるためここでは更新しない
                log.info("✅ [自動修復完了] {} のデータを最新状態に復旧しました。", key);
            }
        } catch (Exception e) {
            log.error("❌ データ修復に失敗しました。次回のTickで再試行します。", e);
        }
    }

    // =========================================================================
    // 読み取り専用の安全なメソッド群（戦略クラスから呼び出される）
    // =========================================================================

    public CandleData getCurrentCandle(MarketKey key) {
        return currentCandleMap.get(key);
    }

    public List<CandleData> getHistoryClone(MarketKey key) {
        List<CandleData> hist = historyMap.get(key);
        return hist == null ? new ArrayList<>() : new ArrayList<>(hist);
    }

    public double getPastCandleClose(MarketKey key, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || barsAgo <= 0 || hist.size() < barsAgo) return 0.0;
        return hist.get(hist.size() - barsAgo).getClose();
    }

    public double getPastCandleOpen(MarketKey key, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || barsAgo <= 0 || hist.size() < barsAgo) return 0.0;
        return hist.get(hist.size() - barsAgo).getOpen();
    }

    public long getPastCandleTime(MarketKey key, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || barsAgo <= 0 || hist.size() < barsAgo) return 0L;
        return hist.get(hist.size() - barsAgo).getTime();
    }

    public double getPastMA(MarketKey key, int period, int barsAgo) {
        if (barsAgo == 0) return calculateCurrentMA(key, period);
        if (barsAgo < 0) return 0.0;
        
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < period + barsAgo - 1) return 0.0;
        
        int startIndex = hist.size() - barsAgo;
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += hist.get(startIndex - i).getClose();
        }
        return sum / period;
    }

    public double getStdDev(MarketKey key, int period, int barsAgo) {
        if (barsAgo < 0) return 0.0;

        if (barsAgo == 0) {
            double sma = calculateCurrentMA(key, period);
            List<CandleData> hist = historyMap.get(key);
            CandleData cur = currentCandleMap.get(key);
            if (hist == null || hist.size() < period - 1 || cur == null) return 0.0;
            
            double sumSq = Math.pow(cur.getClose() - sma, 2);
            for (int i = 1; i < period; i++) {
                sumSq += Math.pow(hist.get(hist.size() - i).getClose() - sma, 2);
            }
            return Math.sqrt(sumSq / period);
        } else {
            double sma = getPastMA(key, period, barsAgo);
            List<CandleData> hist = historyMap.get(key);
            if (hist == null || hist.size() < period + barsAgo - 1) return 0.0;
            
            int startIndex = hist.size() - barsAgo;
            double sumSq = 0;
            for (int i = 0; i < period; i++) {
                sumSq += Math.pow(hist.get(startIndex - i).getClose() - sma, 2);
            }
            return Math.sqrt(sumSq / period);
        }
    }

    public double calculateCurrentMA(MarketKey key, int period) {
        List<CandleData> hist = historyMap.get(key);
        CandleData cur = currentCandleMap.get(key);
        if (hist == null || hist.size() < period - 1 || cur == null) return 0.0;
        
        double sum = cur.getClose();
        for (int i = 1; i < period; i++) {
            sum += hist.get(hist.size() - i).getClose();
        }
        return sum / period;
    }
    
 // =========================================================================
    // チャート初期化用の過去MA一括計算メソッド
    // =========================================================================
    public List<MovingAverageData> calculateHistoricalMA(List<CandleData> candles, int period) {
        List<MovingAverageData> res = new ArrayList<>();
        if (candles == null || candles.size() < period) return res;
        
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += candles.get(i - j).getClose();
            }
            res.add(new MovingAverageData(candles.get(i).getTime(), sum / period));
        }
        return res;
    }
    
 // =========================================================================
    // 高度なテクニカル指標計算メソッド群（移行分）
    // =========================================================================

    public double getPastEMA(MarketKey key, int period, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < 100) return 0.0;
        int endIndex = hist.size() - 1 - barsAgo;
        if (barsAgo == 0) {
            CandleData cur = currentCandleMap.get(key);
            List<Double> prices = getPrices(hist, endIndex, 100);
            if (cur != null) prices.add(cur.getClose());
            return calculateEMA(prices, period);
        } else {
            List<Double> prices = getPrices(hist, endIndex, 100);
            return calculateEMA(prices, period);
        }
    }

    public double getATR(MarketKey key, int period, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < period + barsAgo + 1) return 0.0;
        int endIndex = hist.size() - 1 - barsAgo;
        double trSum = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            CandleData curr = hist.get(i);
            CandleData prev = hist.get(i - 1);
            double tr = Math.max(curr.getHigh() - curr.getLow(), 
                        Math.max(Math.abs(curr.getHigh() - prev.getClose()), 
                                 Math.abs(curr.getLow() - prev.getClose())));
            trSum += tr;
        }
        return trSum / period; 
    }

    public double[] getADX(MarketKey key, int period, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < 100) return new double[]{0,0,0};
        int endIndex = hist.size() - 1 - barsAgo;
        double smoothedTR = 0, smoothedPDM = 0, smoothedNDM = 0;
        for (int i = endIndex - period * 2; i <= endIndex; i++) {
            CandleData curr = hist.get(i); CandleData prev = hist.get(i-1);
            double tr = Math.max(curr.getHigh() - curr.getLow(), Math.max(Math.abs(curr.getHigh() - prev.getClose()), Math.abs(curr.getLow() - prev.getClose())));
            double pdm = (curr.getHigh() - prev.getHigh() > prev.getLow() - curr.getLow()) ? Math.max(curr.getHigh() - prev.getHigh(), 0) : 0;
            double ndm = (prev.getLow() - curr.getLow() > curr.getHigh() - prev.getHigh()) ? Math.max(prev.getLow() - curr.getLow(), 0) : 0;
            if (i == endIndex - period * 2) { smoothedTR = tr; smoothedPDM = pdm; smoothedNDM = ndm; } 
            else { smoothedTR = smoothedTR - (smoothedTR / period) + tr; smoothedPDM = smoothedPDM - (smoothedPDM / period) + pdm; smoothedNDM = smoothedNDM - (smoothedNDM / period) + ndm; }
        }
        double pdi = smoothedTR == 0 ? 0 : 100 * (smoothedPDM / smoothedTR);
        double ndi = smoothedTR == 0 ? 0 : 100 * (smoothedNDM / smoothedTR);
        double dx = (pdi + ndi == 0) ? 0 : 100 * Math.abs(pdi - ndi) / (pdi + ndi);
        return new double[]{dx, pdi, ndi}; 
    }

    public double[] getMACD(MarketKey key, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < 100) return new double[]{0,0,0};
        int endIndex = hist.size() - 1 - barsAgo;
        double ema12 = calculateEMA(getPrices(hist, endIndex, 50), 12);
        double ema26 = calculateEMA(getPrices(hist, endIndex, 50), 26);
        double macd = ema12 - ema26;
        List<Double> macds = new ArrayList<>();
        for(int i = 30; i >= 0; i--) {
            int idx = endIndex - i;
            macds.add(calculateEMA(getPrices(hist, idx, 50), 12) - calculateEMA(getPrices(hist, idx, 50), 26));
        }
        double signal = calculateEMA(macds, 9);
        return new double[]{macd, signal, macd - signal};
    }

    private double calculateEMA(List<Double> prices, int period) {
        if (prices.isEmpty()) return 0;
        double k = 2.0 / (period + 1); double ema = prices.get(0);
        for (int i = 1; i < prices.size(); i++) ema = (prices.get(i) - ema) * k + ema;
        return ema;
    }

    private List<Double> getPrices(List<CandleData> hist, int endIndex, int count) {
        List<Double> res = new ArrayList<>();
        for (int i = Math.max(0, endIndex - count + 1); i <= endIndex; i++) res.add(hist.get(i).getClose());
        return res;
    }

    public boolean isBearishDivergence(MarketKey key, CandleData current, double currentMacdHist) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < 20) return false;
        double highest = 0; int highestIdx = -1;
        for (int i = 1; i <= 20; i++) {
            double h = hist.get(hist.size() - i).getHigh();
            if (h > highest) { highest = h; highestIdx = i; }
        }
        if (highestIdx == -1) return false;
        double pastHist = getMACD(key, highestIdx)[2];
        return current.getHigh() >= highest * 0.999 && currentMacdHist < pastHist;
    }

    public boolean isBullishDivergence(MarketKey key, CandleData current, double currentMacdHist) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < 20) return false;
        double lowest = Double.MAX_VALUE; int lowestIdx = -1;
        for (int i = 1; i <= 20; i++) {
            double l = hist.get(hist.size() - i).getLow();
            if (l < lowest) { lowest = l; lowestIdx = i; }
        }
        if (lowestIdx == -1) return false;
        double pastHist = getMACD(key, lowestIdx)[2];
        return current.getLow() <= lowest * 1.001 && currentMacdHist > pastHist;
    }
    
    public List<com.example.cryptotool.model.SwingPoint> getRecentSwings(MarketKey key, int count) {
        List<CandleData> hist = historyMap.get(key);
        List<com.example.cryptotool.model.SwingPoint> swings = new ArrayList<>();
        if (hist == null || hist.size() < 20) return swings;
        int lookback = 5; 
        Boolean lastFoundWasHigh = null;

        for (int i = hist.size() - lookback - 1; i >= lookback; i--) {
            double currentHigh = hist.get(i).getHigh();
            double currentLow = hist.get(i).getLow();
            boolean isHigh = true, isLow = true;
            for (int j = 1; j <= lookback; j++) {
                if (hist.get(i - j).getHigh() >= currentHigh || hist.get(i + j).getHigh() >= currentHigh) isHigh = false;
                if (hist.get(i - j).getLow() <= currentLow || hist.get(i + j).getLow() <= currentLow) isLow = false;
            }
            
            if (isHigh && isLow) continue;
            
            if (lastFoundWasHigh == null) {
                if (isHigh) { swings.add(0, new com.example.cryptotool.model.SwingPoint(i, currentHigh, true)); lastFoundWasHigh = true; } 
                else if (isLow) { swings.add(0, new com.example.cryptotool.model.SwingPoint(i, currentLow, false)); lastFoundWasHigh = false; }
            } else {
                if (lastFoundWasHigh && isLow) { swings.add(0, new com.example.cryptotool.model.SwingPoint(i, currentLow, false)); lastFoundWasHigh = false; } 
                else if (!lastFoundWasHigh && isHigh) { swings.add(0, new com.example.cryptotool.model.SwingPoint(i, currentHigh, true)); lastFoundWasHigh = true; }
            }
            if (swings.size() == count) break;
        }
        return swings;
    }
    
 // =========================================================================
    // バックテスト専用：過去データの直接注入メソッド（本番データは汚染しません）
    // =========================================================================
    public void addCandleForBacktest(MarketKey key, CandleData candle) {
        historyMap.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(candle);
        currentCandleMap.put(key, candle);
    }
    
 // =========================================================================
    // 外部アクセス用のGetterメソッド
    // =========================================================================
    public java.util.Map<com.example.cryptotool.model.MarketKey, java.util.List<com.example.cryptotool.model.response.ChartInitResponse.CandleData>> getHistoryMap() {
        return historyMap;
    }

    public java.util.Map<com.example.cryptotool.model.MarketKey, com.example.cryptotool.model.response.ChartInitResponse.CandleData> getCurrentCandleMap() {
        return currentCandleMap;
    }
    
 // =========================================================================
    // 追加: 平均出来高の取得
    // =========================================================================
    public double getAverageVolume(MarketKey key, int periods) {
        // 修正: 内部の historyMap から直接データを取得する
        List<CandleData> history = historyMap.get(key); 
        
        if (history == null || history.size() < periods) return 0.0;

        double totalVolume = 0.0;
        int startIndex = history.size() - periods;
        for (int i = startIndex; i < history.size(); i++) {
            totalVolume += history.get(i).getVolume();
        }
        return totalVolume / periods;
    }
    
 // --- MarketDataStore.java に以下のメソッドを追加 ---

    public double getRSI(MarketKey key, int period, int barsAgo) {
        List<CandleData> hist = historyMap.get(key);
        if (hist == null || hist.size() < period + barsAgo + 1) return 50.0;
        int endIndex = hist.size() - 1 - barsAgo;
        
        double gain = 0.0;
        double loss = 0.0;
        
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            double diff = hist.get(i).getClose() - hist.get(i - 1).getClose();
            if (diff > 0) gain += diff;
            else loss -= diff;
        }
        
        if (gain + loss == 0) return 50.0;
        return 100.0 * (gain / (gain + loss));
    }

    public double getMaDeviationRate(MarketKey key, int period, int barsAgo) {
        double currentClose = getPastCandleClose(key, barsAgo);
        double ma = getPastMA(key, period, barsAgo);
        if (ma == 0) return 0.0;
        return (currentClose - ma) / ma * 100.0;
    }
}