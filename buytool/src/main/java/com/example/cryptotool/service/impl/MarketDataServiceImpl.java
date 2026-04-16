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

	private final Map<String, List<CandleData>> historyMap = new ConcurrentHashMap<>();
	private final Map<String, CandleData> currentCandleMap = new ConcurrentHashMap<>();
	private final Map<String, Long> lastOrderTimeMap = new ConcurrentHashMap<>();
	private final Map<String, Boolean> monitorSettings = new ConcurrentHashMap<>();
	private final List<Map<String, Object>> tradeHistoryList = new CopyOnWriteArrayList<>();
	
	private final Map<String, String> positionMap = new ConcurrentHashMap<>();
	private final Map<String, Double> entryPriceMap = new ConcurrentHashMap<>();

	private final boolean IS_DEMO_MODE = true;
	private boolean isSystemReady = false; 

	@PostConstruct
	public void init() {
		log.info("🚀 システム起動: 全通貨・全時間足の非同期初期化を開始します...");
		addSystemLog("SYSTEM BOOTING", "システム初期化中...");

		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				for (Symbol s : Symbol.values()) {
					for (TimeFrame tf : TimeFrame.values()) {
						String key = s.name() + "_" + tf.name();
						List<CandleData> fetched = cryptoCompareClient.getHistoricalCandles(s, tf, 1000);
						historyMap.put(key, fetched);
						if (!fetched.isEmpty())
							currentCandleMap.put(key, fetched.get(fetched.size() - 1));

						positionMap.put(key, "NONE"); // 初期状態はポジションなし
						lastOrderTimeMap.put(key, 0L);
						monitorSettings.put(key, false);
						entryPriceMap.put(key, 0.0); 

						Thread.sleep(150); 
					}
				}
				isSystemReady = true;
				addSystemLog("SYSTEM READY", "全データの準備が完了し、全時間足の監視態勢に入りました。");
				log.info("✅ 初期化完了");
			} catch (Exception e) {
				log.error("初期化中にエラーが発生しました", e);
			}
		});
	}

	public void updateMonitorSetting(String symbol, String timeframe, boolean active) {
		String key = symbol + "_" + timeframe;
		monitorSettings.put(key, active);
		log.info("設定変更: {} -> 監視{}", key, active ? "ON" : "OFF");
	}

	public void enableAllForTimeframe(String timeframe) {
		for (Symbol s : Symbol.values()) {
			String key = s.name() + "_" + timeframe;
			monitorSettings.put(key, true);
		}
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
				.ma25(calculateHistoricalMA(candles, 25)).ma50(calculateHistoricalMA(candles, 50))
				.ma75(calculateHistoricalMA(candles, 75)).ma100(calculateHistoricalMA(candles, 100)).build();
	}

	@Override
	public void processRealtimeTick(TickData tick) {
		if (!isSystemReady) return;
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
		double ma10 = calculateCurrentMA(key, 10);
		double ma25 = calculateCurrentMA(key, 25);
		double ma50 = calculateCurrentMA(key, 50);
		double ma75 = calculateCurrentMA(key, 75);
		double ma100 = calculateCurrentMA(key, 100);

		RealtimeUpdateDto.SignalType signal = checkSignal(key, current);

		if (signal != RealtimeUpdateDto.SignalType.NONE && monitorSettings.getOrDefault(key, false)) {
			executeTrade(tick.getSymbol(), tf, signal, current);
		}

		messagingTemplate.convertAndSend("/topic/" + key, 
			RealtimeUpdateDto.builder().currentCandle(current)
				.currentMa5(ma5).currentMa10(ma10).currentMa25(ma25)
				.currentMa50(ma50).currentMa75(ma75).currentMa100(ma100)
				.signal(signal).build());
	}

	// =========================================================
	// 🧠 高度なシグナル判定エンジン (LONG / SHORT 完全両対応)
	// =========================================================
	
	private RealtimeUpdateDto.SignalType checkSignal(String key, CandleData current) {
		if (current == null) return RealtimeUpdateDto.SignalType.NONE;

		String currentPosition = positionMap.getOrDefault(key, "NONE");

		if ("NONE".equals(currentPosition)) {
			if (checkBuySignal(key, current)) {
				return RealtimeUpdateDto.SignalType.BUY;   // LONGエントリー
			} else if (checkShortSignal(key, current)) {
				return RealtimeUpdateDto.SignalType.SELL;  // SHORTエントリー
			}
		} 
		else if ("LONG".equals(currentPosition)) {
			if (checkExitLongSignal(key, current)) {
				return RealtimeUpdateDto.SignalType.SELL;  // LONG決済
			}
		}
		else if ("SHORT".equals(currentPosition)) {
			if (checkExitShortSignal(key, current)) {
				return RealtimeUpdateDto.SignalType.BUY;   // SHORT決済
			}
		}
		return RealtimeUpdateDto.SignalType.NONE;
	}

	private boolean checkBuySignal(String key, CandleData current) {
		double ma5 = getPastMA(key, 5, 0);
		double ma10 = getPastMA(key, 10, 0);
		double ma25 = getPastMA(key, 25, 0);
		double ma50 = getPastMA(key, 50, 0);
		double ma25_prev = getPastMA(key, 25, 1);
		double ma50_3ago = getPastMA(key, 50, 3);
		
		if (ma5 == 0 || ma10 == 0 || ma25 == 0 || ma50 == 0 || ma25_prev == 0 || ma50_3ago == 0) return false;

		boolean isEnvOk = (ma5 < ma10) && (ma10 < ma25) && (ma25 < ma25_prev) && (ma50 >= ma50_3ago);
		boolean isSupportReached = current.getLow() <= ma50 * 1.001;
		boolean isReboundConfirmed = isLargeBullish(key, current) && getBodyCenter(current) > ma5 && current.getClose() > ma10;

		return isEnvOk && isSupportReached && isReboundConfirmed;
	}

	private boolean checkExitLongSignal(String key, CandleData current) {
		double ma5 = getPastMA(key, 5, 0);
		CandleData prevCandle = getCandle(key, 1); 
		if (ma5 == 0 || prevCandle == null) return false;

		boolean patternA = isBearish(current) && getBodyCenter(current) < ma5 && current.getClose() < ma5;
		boolean patternB = isLargeBullish(key, prevCandle) && isBearish(current) && getBodySize(current) >= getBodySize(prevCandle) * 0.8;
		
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		boolean patternC = entryPrice > 0 && current.getClose() < entryPrice * 0.995;

		return patternA || patternB || patternC;
	}

	private boolean checkShortSignal(String key, CandleData current) {
		double ma5 = getPastMA(key, 5, 0);
		double ma10 = getPastMA(key, 10, 0);
		double ma25 = getPastMA(key, 25, 0);
		double ma50 = getPastMA(key, 50, 0);
		CandleData prev = getCandle(key, 1);

		if (ma5 == 0 || ma10 == 0 || ma25 == 0 || ma50 == 0 || prev == null) return false;

		double ma25_prev = getPastMA(key, 25, 1);
		boolean isDowntrend = (ma10 < ma25) && (ma25 < ma50) && (ma25 < ma25_prev);
		if (!isDowntrend) return false;

		double prevMa10 = getPastMA(key, 10, 1);
		boolean brokeMa10 = prev.getHigh() > prevMa10 || current.getHigh() > ma10;
		if (!brokeMa10) return false;

		double prevMa25 = getPastMA(key, 25, 1);
		double prevMa50 = getPastMA(key, 50, 1);
		boolean touchedMa25Or50 = prev.getHigh() >= prevMa25 * 0.999 || prev.getHigh() >= prevMa50 * 0.999;
		if (!touchedMa25Or50) return false;

		boolean isTriggered = isLargeBearish(key, current) && current.getClose() < ma5 * 0.999;

		return isTriggered;
	}

	private boolean checkExitShortSignal(String key, CandleData current) {
		double ma5 = getPastMA(key, 5, 0);
		if (ma5 == 0) return false;

		boolean isTakeProfit = isBullish(current) && current.getClose() > ma5;
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		boolean isStopLoss = entryPrice > 0 && current.getClose() > entryPrice * 1.005;

		return isTakeProfit || isStopLoss;
	}

	private boolean isBullish(CandleData c) { return c.getClose() > c.getOpen(); } 
	private boolean isBearish(CandleData c) { return c.getClose() < c.getOpen(); } 
	private double getBodySize(CandleData c) { return Math.abs(c.getClose() - c.getOpen()); } 
	private double getBodyCenter(CandleData c) { return (c.getOpen() + c.getClose()) / 2.0; } 

	private boolean isLargeBullish(String key, CandleData c) {
		if (!isBullish(c)) return false;
		return isLargeCandle(key, c);
	}

	private boolean isLargeBearish(String key, CandleData c) {
		if (!isBearish(c)) return false;
		return isLargeCandle(key, c);
	}
	
	private boolean isLargeCandle(String key, CandleData c) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < 10) return false;
		double sum = 0;
		for (int i = 1; i <= 10; i++) sum += getBodySize(hist.get(hist.size() - i));
		double avgBody = sum / 10.0;
		return getBodySize(c) > avgBody * 1.5;
	}

	private CandleData getCandle(String key, int barsAgo) {
		if (barsAgo == 0) return currentCandleMap.get(key);
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < barsAgo) return null;
		return hist.get(hist.size() - barsAgo);
	}

	private double getPastMA(String key, int period, int barsAgo) {
		if (barsAgo == 0) return calculateCurrentMA(key, period);
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo - 1) return 0.0;
		double sum = 0;
		int endIndex = hist.size() - barsAgo; 
		for (int i = 0; i < period; i++) sum += hist.get(endIndex - i).getClose();
		return sum / period;
	}

	private void executeTrade(Symbol symbol, TimeFrame tf, RealtimeUpdateDto.SignalType signal, CandleData candle) {
		String key = symbol.name() + "_" + tf.name();
		if (lastOrderTimeMap.get(key).equals(candle.getTime())) return;
		
		String currentPos = positionMap.getOrDefault(key, "NONE");
		String newPos = currentPos;
		String actionType = "";

		if ("NONE".equals(currentPos)) {
			if (signal == RealtimeUpdateDto.SignalType.BUY) {
				newPos = "LONG";
				actionType = "🟢 [LONG エントリー]";
			} else if (signal == RealtimeUpdateDto.SignalType.SELL) {
				newPos = "SHORT";
				actionType = "🔴 [SHORT エントリー]";
			}
		} else if ("LONG".equals(currentPos) && signal == RealtimeUpdateDto.SignalType.SELL) {
			newPos = "NONE";
			actionType = "✅ [LONG 利益確定/損切]";
		} else if ("SHORT".equals(currentPos) && signal == RealtimeUpdateDto.SignalType.BUY) {
			newPos = "NONE";
			actionType = "✅ [SHORT 利益確定/損切]";
		} else {
			return; 
		}

		Map<String, Object> logTrade = new HashMap<>();
		logTrade.put("time", System.currentTimeMillis() / 1000);
		logTrade.put("symbol", symbol.name());
		logTrade.put("timeframe", tf.name());
		logTrade.put("side", signal.name()); 
		logTrade.put("price", candle.getClose());
		logTrade.put("size", 0.001);

		tradeHistoryList.add(0, logTrade);
		
		// ★ 修正：Objectに明示的にキャストしてメソッドの曖昧さを回避する
		Object payload = logTrade;
		messagingTemplate.convertAndSend("/topic/trades", payload);

		lastOrderTimeMap.put(key, candle.getTime());
		positionMap.put(key, newPos);
		
		if (!"NONE".equals(newPos)) {
			entryPriceMap.put(key, candle.getClose());
		} else {
			entryPriceMap.remove(key); 
		}
		
		log.info("★★★ [自動売買] [{}] {} 注文を実行しました。価格: {} ★★★", key, actionType, candle.getClose());
	}

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
		
		// ★ 修正：Objectに明示的にキャストしてメソッドの曖昧さを回避する
		Object payload = m;
		messagingTemplate.convertAndSend("/topic/trades", payload);
	}
}