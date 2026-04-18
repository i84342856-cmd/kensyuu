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

import lombok.AllArgsConstructor;
import lombok.Data;
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
	private final Map<String, Double> positionSizeMap = new ConcurrentHashMap<>();
	
	private final Map<String, Integer> entryStrategyMap = new ConcurrentHashMap<>();
	private final Map<String, Long> entryCandleTimeMap = new ConcurrentHashMap<>();

	private final boolean IS_DEMO_MODE = true;
	private boolean isSystemReady = false; 
	
	private final double TARGET_TRADE_AMOUNT = 1500000.0;
	
	private final double MIN_SLOPE_THRESHOLD = 0.0003;
	private final double NEARBY_MA_THRESHOLD = 0.005;
	private final double PO_WIDEN_THRESHOLD = 0.002;

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
						
						if (fetched == null) fetched = new ArrayList<>();
						
						if (!fetched.isEmpty()) {
							CandleData last = fetched.remove(fetched.size() - 1);
							currentCandleMap.put(key, last);
						}
						historyMap.put(key, fetched);

						positionMap.put(key, "NONE"); 
						lastOrderTimeMap.put(key, 0L);
						monitorSettings.put(key, false);
						entryPriceMap.put(key, 0.0); 
						positionSizeMap.put(key, 0.0);
						entryStrategyMap.put(key, 0);
						entryCandleTimeMap.put(key, 0L);

						Thread.sleep(1000); 
					}
				}
				isSystemReady = true;
				addSystemLog("SYSTEM READY", "全データの準備が完了し、監視態勢に入りました。");
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

	@Data
	@AllArgsConstructor
	private static class SignalDecision {
		RealtimeUpdateDto.SignalType type;
		int strategyId; 
		String reason;  
	}

	@Override
	public ChartInitResponse getInitialData(Symbol symbol, TimeFrame timeFrame) {
		String key = symbol.name() + "_" + timeFrame.name();
		List<CandleData> candles = new ArrayList<>(historyMap.getOrDefault(key, new ArrayList<>()));
		
		CandleData current = currentCandleMap.get(key);
		if (current != null) candles.add(current);

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
			if (current != null) {
				historyMap.get(key).add(current);
			}
			current = CandleData.builder().time(candleStart).open(price).high(price).low(price).close(price).build();
			currentCandleMap.put(key, current);
		} else {
			current.setClose(price);
			current.setHigh(Math.max(current.getHigh(), price));
			current.setLow(Math.min(current.getLow(), price));
		}

		SignalDecision decision = checkSignal(tf, key, current);

		if (decision != null && decision.getType() != RealtimeUpdateDto.SignalType.NONE 
				&& monitorSettings.getOrDefault(key, false)) {
			executeTrade(tick.getSymbol(), tf, decision, current);
		}

		messagingTemplate.convertAndSend("/topic/" + key, 
			RealtimeUpdateDto.builder().currentCandle(current)
				.currentMa5(calculateCurrentMA(key, 5)).currentMa10(calculateCurrentMA(key, 10)).currentMa25(calculateCurrentMA(key, 25))
				.currentMa50(calculateCurrentMA(key, 50)).currentMa75(calculateCurrentMA(key, 75)).currentMa100(calculateCurrentMA(key, 100))
				.signal(decision != null ? decision.getType() : RealtimeUpdateDto.SignalType.NONE).build());
	}
	
	private SignalDecision checkSignal(TimeFrame tf, String key, CandleData current) {
		if (current == null) return null;
		
		//if ("M1".equals(tf.name())) return null;

		String currentPosition = positionMap.getOrDefault(key, "NONE");

		if ("NONE".equals(currentPosition)) {
			SignalDecision buySignal = checkBuySignal(key, current);
			if (buySignal != null) return buySignal;

			SignalDecision shortSignal = checkShortSignal(key, current);
			if (shortSignal != null) return shortSignal;
		} 
		else if ("LONG".equals(currentPosition)) {
			return checkExitLongSignal(tf, key, current);
		}
		else if ("SHORT".equals(currentPosition)) {
			return checkExitShortSignal(tf, key, current);
		}
		return null;
	}

	private SignalDecision checkBuySignal(String key, CandleData current) {
		double ma5 = getPastMA(key, 5, 0), ma5Prev = getPastMA(key, 5, 1);       
		double ma10 = getPastMA(key, 10, 0), ma10Prev = getPastMA(key, 10, 1);     
		double ma25 = getPastMA(key, 25, 0), ma25Prev = getPastMA(key, 25, 1);     
		double ma50 = getPastMA(key, 50, 0), ma50Prev = getPastMA(key, 50, 1);
		double ma75 = getPastMA(key, 75, 0), ma75Prev = getPastMA(key, 75, 1);
		double ma100 = getPastMA(key, 100, 0), ma100Prev = getPastMA(key, 100, 1);

		if (ma5 == 0 || ma75Prev == 0) return null;

		boolean isMa5Up = isUpwardTrend(ma5, ma5Prev);
		boolean isMa10Up = isUpwardTrend(ma10, ma10Prev);
		boolean isMa25Up = isUpwardTrend(ma25, ma25Prev);
		boolean isMa50Down = isDownwardTrend(ma50, ma50Prev);
		boolean isMa50Up = isUpwardTrend(ma50, ma50Prev);
		boolean isMa75Up = isUpwardTrend(ma75, ma75Prev);
		boolean isMa100Up = isUpwardTrend(ma100, ma100Prev);

		boolean cross10 = (ma5Prev <= ma10Prev) && (ma5 > ma10);
		if (cross10 && isMa5Up && isMa10Up && isMa25Up) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 1, "GC(5&10) + 25MA同調");
		}
		boolean cross25 = (ma5Prev <= ma25Prev) && (ma5 > ma25);
		boolean ma50Nearby = (Math.abs(ma50 - current.getClose()) / current.getClose()) < NEARBY_MA_THRESHOLD;
		if (cross25 && isMa5Up && isMa25Up && isMa10Up && !(ma50Nearby && isMa50Down)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 1, "GC(5&25) + 50MAレジスタンス回避");
		}

		boolean poUp = (ma5 > ma10) && (ma10 > ma25) && isMa5Up && isMa10Up && isMa25Up;
		boolean widenUp = ((ma5 - ma10) / ma10) >= PO_WIDEN_THRESHOLD;
		if (poUp && widenUp) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 2, "PO上昇中 + 角度急拡大(モメンタム)");
		}

		boolean isMa5Down = isDownwardTrend(ma5, ma5Prev);
		boolean isMa10Down = isDownwardTrend(ma10, ma10Prev);
		boolean isMa25Down = isDownwardTrend(ma25, ma25Prev);
		boolean poDown = (ma5 < ma10) && (ma10 < ma25) && isMa5Down && isMa10Down && isMa25Down;
		boolean touch75FromAbove = (current.getLow() <= ma75) && (current.getClose() >= ma75); 
		if (poDown && isMa75Up && touch75FromAbove) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 3, "75MA(上向き) サポート反発狙い");
		}

		// 【追加戦略4】買い
		boolean isPoBuyExcept5 = (ma10 > ma25) && (ma25 > ma50) && (ma50 > ma75) && (ma75 > ma100);
		boolean isAllUpExcept5 = isMa10Up && isMa25Up && isMa50Up && isMa75Up && isMa100Up;
		if (isPoBuyExcept5 && isAllUpExcept5) {
			boolean approached = false;
			for(int i = 1; i <= 5; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa10 = getPastMA(key, 10, i);
				if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD) {
					approached = true; break;
				}
			}
			if (approached && isMa5Up) {
				double highest10 = getHighestHigh(key, 10, 1);
				if (current.getClose() > highest10) {
					return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 4, "PO(5以外) + 10MA接近反発 + 高値更新");
				}
			}
		}

		return null;
	}

	private SignalDecision checkShortSignal(String key, CandleData current) {
		double ma5 = getPastMA(key, 5, 0), ma5Prev = getPastMA(key, 5, 1);       
		double ma10 = getPastMA(key, 10, 0), ma10Prev = getPastMA(key, 10, 1);     
		double ma25 = getPastMA(key, 25, 0), ma25Prev = getPastMA(key, 25, 1);     
		double ma50 = getPastMA(key, 50, 0), ma50Prev = getPastMA(key, 50, 1);
		double ma75 = getPastMA(key, 75, 0), ma75Prev = getPastMA(key, 75, 1);
		double ma100 = getPastMA(key, 100, 0), ma100Prev = getPastMA(key, 100, 1);

		if (ma5 == 0 || ma75Prev == 0) return null;

		boolean isMa5Down = isDownwardTrend(ma5, ma5Prev);
		boolean isMa10Down = isDownwardTrend(ma10, ma10Prev);
		boolean isMa25Down = isDownwardTrend(ma25, ma25Prev);
		boolean isMa50Down = isDownwardTrend(ma50, ma50Prev);
		boolean isMa75Down = isDownwardTrend(ma75, ma75Prev);
		boolean isMa100Down = isDownwardTrend(ma100, ma100Prev);
		
		boolean isMa5Up = isUpwardTrend(ma5, ma5Prev);
		boolean isMa10Up = isUpwardTrend(ma10, ma10Prev);
		boolean isMa25Up = isUpwardTrend(ma25, ma25Prev);
		boolean isMa50Up = isUpwardTrend(ma50, ma50Prev);

		boolean cross10 = (ma5Prev >= ma10Prev) && (ma5 < ma10);
		if (cross10 && isMa5Down && isMa10Down && isMa25Down) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 1, "DC(5&10) + 25MA同調");
		}
		boolean cross25 = (ma5Prev >= ma25Prev) && (ma5 < ma25);
		boolean ma50Nearby = (Math.abs(ma50 - current.getClose()) / current.getClose()) < NEARBY_MA_THRESHOLD;
		if (cross25 && isMa5Down && isMa25Down && isMa10Down && !(ma50Nearby && isMa50Up)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 1, "DC(5&25) + 50MAサポート回避");
		}

		boolean poDown = (ma5 < ma10) && (ma10 < ma25) && isMa5Down && isMa10Down && isMa25Down;
		boolean widenDown = ((ma10 - ma5) / ma10) >= PO_WIDEN_THRESHOLD; 
		if (poDown && widenDown) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 2, "PO下降中 + 角度急拡大(モメンタム)");
		}

		boolean poUp = (ma5 > ma10) && (ma10 > ma25) && isMa5Up && isMa10Up && isMa25Up;
		boolean touch75FromBelow = (current.getHigh() >= ma75) && (current.getClose() <= ma75);
		if (poUp && isMa75Down && touch75FromBelow) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 3, "75MA(下向き) レジスタンス反発狙い");
		}

		// 【追加戦略4】売り
		boolean isPoShortExcept5 = (ma10 < ma25) && (ma25 < ma50) && (ma50 < ma75) && (ma75 < ma100);
		boolean isAllDownExcept5 = isMa10Down && isMa25Down && isMa50Down && isMa75Down && isMa100Down;
		if (isPoShortExcept5 && isAllDownExcept5) {
			boolean approached = false;
			for(int i = 1; i <= 5; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa10 = getPastMA(key, 10, i);
				if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD) {
					approached = true; break;
				}
			}
			if (approached && isMa5Down) {
				double lowest10 = getLowestLow(key, 10, 1);
				if (current.getClose() < lowest10) {
					return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 4, "PO(5以外) + 10MA接近反発 + 安値更新");
				}
			}
		}

		boolean wasMa5Up = getPastMA(key, 5, 1) > getPastMA(key, 5, 2);
		boolean is5and10Up = (wasMa5Up || isMa5Up) && isMa10Up;

		// 【追加戦略5】売り
		boolean is25to75Down = isMa25Down && isMa50Down && isMa75Down;
		boolean orderStr5 = (ma10 < ma5) && (ma5 < ma25) && (ma25 < ma50) && (ma50 < ma75) && (ma75 < ma100);
		if (is5and10Up && is25to75Down && orderStr5) {
			boolean approached = false;
			for(int i = 1; i <= 3; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa25 = getPastMA(key, 25, i);
				if (pMa25 > 0 && Math.abs(pMa5 - pMa25) / pMa25 <= NEARBY_MA_THRESHOLD) {
					approached = true; break;
				}
			}
			if (approached && isBearishSwallow(key, current)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 5, "25MA接近反発 + 上昇分を包み込む陰線");
			}
		}

		// 【追加戦略6】売り
		boolean is50to100Down = isMa50Down && isMa75Down && isMa100Down;
		boolean orderStr6 = ((ma10 < ma5) || (ma25 < ma5)) && (ma5 < ma50) && (ma50 < ma75) && (ma75 < ma100);
		if (is5and10Up && is50to100Down && orderStr6) {
			boolean approached = false;
			for(int i = 1; i <= 3; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa50 = getPastMA(key, 50, i);
				if (pMa50 > 0 && Math.abs(pMa5 - pMa50) / pMa50 <= NEARBY_MA_THRESHOLD) {
					approached = true; break;
				}
			}
			if (approached && isBearishSwallow(key, current)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 6, "50MA接近反発 + 上昇分を包み込む陰線");
			}
		}

		return null;
	}

	private SignalDecision checkExitLongSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0) return null;

		int strategyId = entryStrategyMap.getOrDefault(key, 1);
		long entryTime = entryCandleTimeMap.getOrDefault(key, 0L);
		long candleSeconds = tf.getSeconds();

		if ((strategyId == 2 || strategyId == 4) && current.getTime() >= entryTime + (3 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "時間経過強制決済(3本目)");
		}
		if (strategyId == 3 && current.getTime() >= entryTime + (1 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 3, "時間経過強制決済(1本目)");
		}

		double targetPct = getTargetPercentage(tf);
		if (current.getClose() >= entryPrice * (1.0 + targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "目標利益到達(TP)");
		}
		if (current.getClose() <= entryPrice * (1.0 - targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "損切り到達(SL)");
		}

		return null;
	}

	private SignalDecision checkExitShortSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0) return null;

		int strategyId = entryStrategyMap.getOrDefault(key, 1);
		long entryTime = entryCandleTimeMap.getOrDefault(key, 0L);
		long candleSeconds = tf.getSeconds();

		if ((strategyId == 2 || strategyId == 4) && current.getTime() >= entryTime + (3 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "時間経過強制決済(3本目)");
		}
		if (strategyId == 3 && current.getTime() >= entryTime + (1 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 3, "時間経過強制決済(1本目)");
		}
		if ((strategyId == 5 || strategyId == 6) && current.getTime() >= entryTime + (2 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "時間経過強制決済(2本目)");
		}

		double targetPct = getTargetPercentage(tf);
		if (current.getClose() <= entryPrice * (1.0 - targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "目標利益到達(TP)");
		}
		if (current.getClose() >= entryPrice * (1.0 + targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "損切り到達(SL)");
		}

		return null;
	}

	private double getTargetPercentage(TimeFrame tf) {
		switch (tf.name()) {
			case "M1":  return 0.0015;
			case "M5":  return 0.003;
			case "M15": return 0.005;
			case "M30": return 0.008;
			case "H1":  return 0.01;
			case "H4":  return 0.02;
			case "D1":  return 0.05;
			default:    return 0.01;
		}
	}

	private boolean isUpwardTrend(double currentMa, double prevMa) {
		if (prevMa <= 0) return false;
		return ((currentMa - prevMa) / prevMa) >= MIN_SLOPE_THRESHOLD;
	}

	private boolean isDownwardTrend(double currentMa, double prevMa) {
		if (prevMa <= 0) return false;
		return ((currentMa - prevMa) / prevMa) <= -MIN_SLOPE_THRESHOLD;
	}

	private double calculateLotSize(double price) {
		if (price <= 0) return 0.001;
		double size = TARGET_TRADE_AMOUNT / price;
		return Math.round(size * 10000.0) / 10000.0;
	}

	private double getPastMA(String key, int period, int barsAgo) {
		if (barsAgo == 0) return calculateCurrentMA(key, period);
		List<CandleData> hist = historyMap.get(key);
		
		int startIndex = hist.size() - barsAgo;
		if (hist == null || startIndex - period + 1 < 0) return 0.0;
		
		double sum = 0;
		for (int i = 0; i < period; i++) {
			sum += hist.get(startIndex - i).getClose();
		}
		return sum / period;
	}

	private void executeTrade(Symbol symbol, TimeFrame tf, SignalDecision decision, CandleData candle) {
		String key = symbol.name() + "_" + tf.name();
		if (lastOrderTimeMap.get(key).equals(candle.getTime())) return; 
		
		String currentPos = positionMap.getOrDefault(key, "NONE");
		String newPos = currentPos;
		String actionType = "";
		double tradeSize = 0.001;

		if ("NONE".equals(currentPos)) {
			tradeSize = calculateLotSize(candle.getClose());
			
			if (decision.getType() == RealtimeUpdateDto.SignalType.BUY) {
				newPos = "LONG";
				actionType = "🟢 [LONG] " + decision.getReason();
			} else if (decision.getType() == RealtimeUpdateDto.SignalType.SELL) {
				newPos = "SHORT";
				actionType = "🔴 [SHORT] " + decision.getReason();
			}
		} else {
			tradeSize = positionSizeMap.getOrDefault(key, 0.001);
			
			if ("LONG".equals(currentPos) && decision.getType() == RealtimeUpdateDto.SignalType.SELL) {
				newPos = "NONE";
				actionType = "✅ [LONG決済] " + decision.getReason();
			} else if ("SHORT".equals(currentPos) && decision.getType() == RealtimeUpdateDto.SignalType.BUY) {
				newPos = "NONE";
				actionType = "✅ [SHORT決済] " + decision.getReason();
			} else {
				return; 
			}
		}

		Map<String, Object> logTrade = new HashMap<>();
		logTrade.put("time", System.currentTimeMillis() / 1000);
		logTrade.put("symbol", symbol.name());
		logTrade.put("timeframe", tf.name());
		logTrade.put("side", decision.getType().name()); 
		logTrade.put("price", candle.getClose());
		logTrade.put("size", tradeSize); 
		logTrade.put("message", actionType); 

		tradeHistoryList.add(0, logTrade);
		messagingTemplate.convertAndSend("/topic/trades", (Object) logTrade);

		lastOrderTimeMap.put(key, candle.getTime());
		positionMap.put(key, newPos);
		
		if (!"NONE".equals(newPos)) {
			entryPriceMap.put(key, candle.getClose());
			positionSizeMap.put(key, tradeSize); 
			entryStrategyMap.put(key, decision.getStrategyId()); 
			entryCandleTimeMap.put(key, candle.getTime());       
		} else {
			entryPriceMap.remove(key); 
			positionSizeMap.remove(key); 
			entryStrategyMap.remove(key);
			entryCandleTimeMap.remove(key);
		}
		
		log.info("★★★ [自動売買] [{}] {} 注文を実行しました。価格: {}, 数量: {} ★★★", key, actionType, candle.getClose(), tradeSize);
	}

	private double calculateCurrentMA(String key, int period) {
		List<CandleData> hist = historyMap.get(key);
		CandleData cur = currentCandleMap.get(key);
		
		if (hist == null || hist.size() < period - 1) return 0;
		
		double sum = cur.getClose(); 
		for (int i = 1; i < period; i++) {
			sum += hist.get(hist.size() - i).getClose();
		}
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
		messagingTemplate.convertAndSend("/topic/trades", (Object) m);
	}

	// =========================================================
	// 🆕 追加補助メソッド：高値・安値・包み線判定
	// =========================================================

	private double getHighestHigh(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo) return Double.MAX_VALUE;
		double highest = 0;
		int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) {
			highest = Math.max(highest, hist.get(endIndex - i).getHigh());
		}
		return highest;
	}

	private double getLowestLow(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo) return 0.0;
		double lowest = Double.MAX_VALUE;
		int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) {
			lowest = Math.min(lowest, hist.get(endIndex - i).getLow());
		}
		return lowest;
	}

	private boolean isBearishSwallow(String key, CandleData current) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < 3) return false;
		CandleData c1 = hist.get(hist.size() - 1);
		CandleData c2 = hist.get(hist.size() - 2);
		CandleData c3 = hist.get(hist.size() - 3);

		boolean isCurrentBearish = current.getClose() < current.getOpen();
		boolean isC1Bearish = c1.getClose() < c1.getOpen();
		
		// パターン1: 1本の陰線(current)が、直近の上昇(c1, c2)を打ち消す（c2の始値より低く終わる）
		boolean swallow1 = isCurrentBearish && (current.getClose() < Math.min(c2.getOpen(), c2.getClose()));
		
		// パターン2: 2本の陰線(current, c1)が、その前の上昇(c2, c3)を打ち消す（c3の始値より低く終わる）
		boolean swallow2 = isCurrentBearish && isC1Bearish && (current.getClose() < Math.min(c3.getOpen(), c3.getClose()));
		
		return swallow1 || swallow2;
	}
}