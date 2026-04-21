package com.example.cryptotool.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import jakarta.annotation.PostConstruct;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.cryptotool.entity.TradeLog;
import com.example.cryptotool.infrastructure.BitFlyerPrivateClient;
import com.example.cryptotool.infrastructure.CryptoCompareClient;
import com.example.cryptotool.model.TickData;
import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;
import com.example.cryptotool.model.response.ChartInitResponse;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.model.response.RealtimeUpdateDto;
import com.example.cryptotool.repository.TradeLogRepository;
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

	private final TradeLogRepository tradeLogRepository;

	private final Map<String, List<CandleData>> historyMap = new ConcurrentHashMap<>();
	private final Map<String, CandleData> currentCandleMap = new ConcurrentHashMap<>();
	private final Map<String, Long> lastOrderTimeMap = new ConcurrentHashMap<>();
	private final Map<String, Boolean> monitorSettings = new ConcurrentHashMap<>();

	private final Map<String, String> positionMap = new ConcurrentHashMap<>();
	private final Map<String, Double> entryPriceMap = new ConcurrentHashMap<>();
	private final Map<String, Double> positionSizeMap = new ConcurrentHashMap<>();

	private final Map<String, Integer> entryStrategyMap = new ConcurrentHashMap<>();
	private final Map<String, Long> entryCandleTimeMap = new ConcurrentHashMap<>();

	private final boolean IS_DEMO_MODE = true;
	private boolean isSystemReady = false;

	// 【修正】取引額を150万から40万（30〜50万の範囲）へ引き下げ
	private final double TARGET_TRADE_AMOUNT = 400000.0;

	// --- 戦略のON/OFF切り替えスイッチ (UIから動的変更可能) ---
	private final Map<String, Boolean> strategySettings = new ConcurrentHashMap<>();
	{
		strategySettings.put("1", true);
		strategySettings.put("2", true);
		strategySettings.put("3", true);
		strategySettings.put("4", true);
		strategySettings.put("5", true);
		strategySettings.put("6", true);
		strategySettings.put("9", false);
		strategySettings.put("92", true);
		strategySettings.put("93", false);
		strategySettings.put("94", true);
		strategySettings.put("10", true); 
		strategySettings.put("12", false);
		strategySettings.put("22", false);
		strategySettings.put("32", true);
		strategySettings.put("42", true);
		strategySettings.put("52", true);
		strategySettings.put("62", true);
	}

	// --- 各種閾値 ---
	private final double MIN_SLOPE_THRESHOLD = 0.0003;
	private final double NEARBY_MA_THRESHOLD = 0.005;
	private final double PO_WIDEN_THRESHOLD = 0.002;
	private final double MIN_SLOPE_THRESHOLD_RELAXED = 0.0001;
	private final double NEARBY_MA_THRESHOLD_RELAXED = 0.01;

	private final double STRATEGY3_MA5_BUFFER = 0.002;

	// 【修正】戦略9系（92, 93, 94）の損切り額を-6000円に拡大
	private final double BB_SQUEEZE_THRESHOLD = 0.01; 
	private final double MA_FLAT_THRESHOLD = 0.0005; 
	private final double MAX_LOSS_JPY = -6000.0; 

	// 【修正】戦略10の損切り額を-8000円に拡大
	private final double STRATEGY10_MAX_LOSS_JPY = -5000.0; 

	private boolean isTargetSymbol(Symbol s) {
		return true; 
	}

	@PostConstruct
	public void init() {
		log.info("🚀 システム起動: 全通貨の 5分足以上の非同期初期化を開始します...");
		addSystemLog("SYSTEM BOOTING", "システム初期化中(全通貨・5分足以上)...");

		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				for (Symbol s : Symbol.values()) {
					if (!isTargetSymbol(s)) continue;

					for (TimeFrame tf : TimeFrame.values()) {
						if (tf == TimeFrame.M1) continue; 

						String key = s.name() + "_" + tf.name();
						List<CandleData> fetched = cryptoCompareClient.getHistoricalCandles(s, tf, 1000);

						if (fetched == null) fetched = new ArrayList<>();

						if (!fetched.isEmpty()) {
							CandleData last = fetched.remove(fetched.size() - 1);
							currentCandleMap.put(key, last);
						}
						historyMap.put(key, fetched);

						Optional<TradeLog> optLatestLog = tradeLogRepository
								.findFirstBySymbolAndTimeframeOrderByTimeDesc(s.name(), tf.name());
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

								log.info("🔄 [記憶復元] {} の {} ポジション (価格: {}) をDBから復元し、監視を再開します。", key, restoredPos, latestLog.getPrice());
							}
						}

						if (!hasPosition) {
							positionMap.put(key, "NONE");
							entryPriceMap.put(key, 0.0);
							positionSizeMap.put(key, 0.0);
							entryStrategyMap.put(key, 0);
							entryCandleTimeMap.put(key, 0L);
						}

						lastOrderTimeMap.put(key, 0L);
						monitorSettings.put(key, hasPosition);

						Thread.sleep(1000);
					}
				}
				isSystemReady = true;
				addSystemLog("SYSTEM READY", "全データの準備とポジション復元が完了しました。");
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

	public Map<String, Boolean> getMonitorSettings() {
		return monitorSettings;
	}

	public void updateStrategySetting(String id, boolean active) {
		strategySettings.put(id, active);
		log.info("戦略変更: 戦略{} -> {}", id, active ? "ON" : "OFF");
	}

	public Map<String, Boolean> getStrategySettings() {
		return strategySettings;
	}

	public List<TradeLog> getTradeHistory() {
		return tradeLogRepository.findTop100ByOrderByTimeDesc();
	}

	@Override
	public List<TradeLog> getAllTradeHistory() {
		return tradeLogRepository.findAllByOrderByTimeDesc();
	}

	@Override
	public List<TradeLog> getTradeLogsForChart(Symbol symbol, TimeFrame tf) {
		return tradeLogRepository.findAllBySymbolAndTimeframeOrderByTimeAsc(symbol.name(), tf.name());
	}

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
		if (!isTargetSymbol(tick.getSymbol())) return;

		for (TimeFrame tf : TimeFrame.values()) {
			if (tf == TimeFrame.M1) continue; 
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
						.currentMa5(calculateCurrentMA(key, 5)).currentMa10(calculateCurrentMA(key, 10))
						.currentMa25(calculateCurrentMA(key, 25)).currentMa50(calculateCurrentMA(key, 50))
						.currentMa75(calculateCurrentMA(key, 75)).currentMa100(calculateCurrentMA(key, 100))
						.signal(decision != null ? decision.getType() : RealtimeUpdateDto.SignalType.NONE).build());
	}

	private SignalDecision checkSignal(TimeFrame tf, String key, CandleData current) {
		if (current == null) return null;

		String currentPosition = positionMap.getOrDefault(key, "NONE");

		if ("NONE".equals(currentPosition)) {
			SignalDecision buySignal = checkBuySignal(key, current);
			if (buySignal != null) return buySignal;

			SignalDecision shortSignal = checkShortSignal(key, current);
			if (shortSignal != null) return shortSignal;
		} else if ("LONG".equals(currentPosition)) {
			return checkExitLongSignal(tf, key, current);
		} else if ("SHORT".equals(currentPosition)) {
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

		if (ma5 == 0) return null;

		boolean isMa5Up = isUpwardTrend(ma5, ma5Prev);
		boolean isMa10Up = isUpwardTrend(ma10, ma10Prev);
		boolean isMa25Up = isUpwardTrend(ma25, ma25Prev);
		boolean isMa50Down = isDownwardTrend(ma50, ma50Prev);
		boolean isMa50Up = isUpwardTrend(ma50, ma50Prev);
		boolean isMa75Up = isUpwardTrend(ma75, ma75Prev);

		if (strategySettings.getOrDefault("1", false)) {
			boolean cross10 = (ma5Prev <= ma10Prev) && (ma5 > ma10);
			if (cross10 && isMa5Up && isMa10Up && isMa25Up) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 1, "【戦略1】GC(5&10)+25MA同調");
			boolean cross25 = (ma5Prev <= ma25Prev) && (ma5 > ma25);
			boolean ma50Nearby = (Math.abs(ma50 - current.getClose()) / current.getClose()) < NEARBY_MA_THRESHOLD;
			if (cross25 && isMa5Up && isMa25Up && isMa10Up && !(ma50Nearby && isMa50Down)) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 1, "【戦略1】GC(5&25)+50MAレジスタンス回避");
		}

		if (strategySettings.getOrDefault("2", false)) {
			boolean poUpShort = (ma5 > ma10) && (ma10 > ma25);
			boolean poUpLong = (ma25 > ma50) && (ma50 > ma75);
			boolean isMa25Up001 = isSlopeGreaterThanOrEqual(ma25, ma25Prev, 0.0001);
			boolean isMa50Pos = isSlopePositive(ma50, ma50Prev);
			boolean isMa75Pos = isSlopePositive(ma75, ma75Prev);
			double ma5_2 = getPastMA(key, 5, 2);
			double ma10_2 = getPastMA(key, 10, 2);
			boolean narrowBeforeUp = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.001;
			boolean widenUp = ((ma5 - ma10) / ma10) >= PO_WIDEN_THRESHOLD;

			if (poUpShort && poUpLong && isMa5Up && isMa10Up && isMa25Up001 && isMa50Pos && isMa75Pos && narrowBeforeUp && widenUp) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 2, "【戦略2】PO上昇+初動急拡大(スクイーズ解放)");
			}
		}

		if (strategySettings.getOrDefault("3", false)) {
			boolean isMa5Down = isDownwardTrend(ma5, ma5Prev);
			boolean shortTermDown = (ma5 < ma10) && isMa5Down;
			double prevLow3 = getLowestLow(key, 3, 1);
			double lowest3 = (prevLow3 > 0) ? Math.min(prevLow3, current.getLow()) : current.getLow();
			boolean approach75 = (lowest3 <= ma75 * 1.002) && (current.getClose() > ma75);
			boolean approach50 = (lowest3 <= ma50 * 1.002) && (current.getClose() > ma50);
			boolean confirmedRebound = isUpwardTrend(ma5, ma5Prev) && (current.getClose() > ma5);
			boolean isMa50UpStrict3 = isSlopeGreaterThanOrEqual(ma50, ma50Prev, 0.0002);
			boolean isMa75UpStrict3 = isSlopeGreaterThanOrEqual(ma75, ma75Prev, 0.0001);

			if (shortTermDown && confirmedRebound) {
				if (isMa50UpStrict3 && approach50) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 3, "【戦略3】50MA(0.02%↑)サポート接近+反発");
				if (isMa75UpStrict3 && approach75) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 3, "【戦略3】75MA(0.01%↑)サポート接近+反発");
			}
		}

		if (strategySettings.getOrDefault("4", false)) {
			boolean isPoBuyExcept5 = (ma10 > ma25) && (ma25 > ma50) && (ma50 > ma75);
			boolean isAllUpExcept5 = isMa10Up && isMa25Up && isMa50Up && isMa75Up;
			if (isPoBuyExcept5 && isAllUpExcept5) {
				boolean approached = false;
				for (int i = 1; i <= 5; i++) {
					double pMa5 = getPastMA(key, 5, i);
					double pMa10 = getPastMA(key, 10, i);
					if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD) { approached = true; break; }
				}
				if (approached && isMa5Up) {
					double highest6 = getHighestHigh(key, 6, 1);
					if (current.getClose() > highest6) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 4, "【戦略4】PO(10-75)+10MA接近反発+高値更新");
				}
			}
		}

		boolean crossUp = getPastCandleClose(key, 2) <= getPastMA(key, 5, 2) && getPastCandleClose(key, 1) > getPastMA(key, 5, 1);
		boolean crossDown = getPastCandleClose(key, 2) >= getPastMA(key, 5, 2) && getPastCandleClose(key, 1) < getPastMA(key, 5, 1);

		if (strategySettings.getOrDefault("92", false)) {
			if (crossUp && ma10 >= ma10Prev && ma25 >= ma25Prev && ma50 >= ma50Prev) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 92, "【戦略9-2】確定足MA5上抜け ＋ MA10/25/50上向き同調（ドテン買い）");
			}
		}

		if (strategySettings.getOrDefault("9", false)) {
			if (crossUp) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 9, "【戦略9】確定足でMA5上抜け（ドテン買い）");
			}
		}

		if (strategySettings.getOrDefault("93", false)) {
			if (crossDown) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 93, "【戦略9-3】確定足MA5下抜けの逆張り（ドテン買い）");
			}
		}

		if (strategySettings.getOrDefault("94", false)) {
			double sma20 = getPastMA(key, 20, 0);
			double stdDev20 = getStdDev(key, 20, 0);
			double bandWidth = (sma20 > 0) ? (4 * stdDev20) / sma20 : 0;
			
			boolean isSqueeze = bandWidth > 0 && bandWidth <= BB_SQUEEZE_THRESHOLD;
			boolean isMa75Flat = Math.abs(ma75 - ma75Prev) / ma75Prev <= MA_FLAT_THRESHOLD;

			if (crossDown && (isSqueeze || isMa75Flat)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 94, "【戦略9-4】MA5下抜け ＋ レンジ相場(BB収縮orMA横ばい)で逆張り買い");
			}
		}

		if (strategySettings.getOrDefault("10", false)) {
			double c1_close = getPastCandleClose(key, 1);
			double c2_close = getPastCandleClose(key, 2);
			
			double c1_sma20 = getPastMA(key, 20, 1);
			double c1_std = getStdDev(key, 20, 1);
			double c1_lower = c1_sma20 - (2 * c1_std);
			
			double c2_sma20 = getPastMA(key, 20, 2);
			double c2_std = getStdDev(key, 20, 2);
			double c2_lower = c2_sma20 - (2 * c2_std);

			if (c1_close < c1_lower && c2_close >= c2_lower) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 10, "【戦略10】BB下限(-2σ)下抜け逆張り買い");
			}
		}

		if (strategySettings.getOrDefault("22", false)) {
			boolean poUpShort = (ma5 > ma10) && (ma10 > ma25);
			boolean poUpLong = (ma25 > ma50) && (ma50 > ma75);
			boolean isMa5Up22 = isSlopeGreaterThanOrEqual(ma5, ma5Prev, MIN_SLOPE_THRESHOLD);
			boolean isMa10Up22 = isSlopeGreaterThanOrEqual(ma10, ma10Prev, 0.0005);
			boolean isMa25Up22 = isSlopeGreaterThanOrEqual(ma25, ma25Prev, 0.0005);
			double ma5_2 = getPastMA(key, 5, 2);
			double ma10_2 = getPastMA(key, 10, 2);
			boolean narrowBeforeUp22 = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.002;
			boolean widenUp22 = ((ma5 - ma10) / ma10) >= 0.002;
			boolean isMa50Pos22 = isSlopePositive(ma50, ma50Prev);

			if (poUpShort && poUpLong && isMa5Up22 && isMa10Up22 && isMa25Up22 && narrowBeforeUp22 && widenUp22 && isMa50Pos22) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 22, "【戦略22】ブラッシュアップ版PO上昇中");
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

		if (ma5 == 0) return null;

		boolean isMa5Down = isDownwardTrend(ma5, ma5Prev);
		boolean isMa10Down = isDownwardTrend(ma10, ma10Prev);
		boolean isMa25Down = isDownwardTrend(ma25, ma25Prev);
		boolean isMa50Down = isDownwardTrend(ma50, ma50Prev);
		boolean isMa75Down = isDownwardTrend(ma75, ma75Prev);

		boolean isMa5Up = isUpwardTrend(ma5, ma5Prev);
		boolean isMa10Up = isUpwardTrend(ma10, ma10Prev);
		boolean isMa50Up = isUpwardTrend(ma50, ma50Prev);

		if (strategySettings.getOrDefault("1", false)) {
			boolean cross10 = (ma5Prev >= ma10Prev) && (ma5 < ma10);
			if (cross10 && isMa5Down && isMa10Down && isMa25Down) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 1, "【戦略1】DC(5&10)+25MA同調");
			boolean cross25 = (ma5Prev >= ma25Prev) && (ma5 < ma25);
			boolean ma50Nearby = (Math.abs(ma50 - current.getClose()) / current.getClose()) < NEARBY_MA_THRESHOLD;
			if (cross25 && isMa5Down && isMa25Down && isMa10Down && !(ma50Nearby && isMa50Up)) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 1, "【戦略1】DC(5&25)+50MAサポート回避");
		}

		if (strategySettings.getOrDefault("2", false)) {
			boolean poDownShort = (ma5 < ma10) && (ma10 < ma25);
			boolean poDownLong = (ma25 < ma50) && (ma50 < ma75);
			boolean isMa25Down001 = isSlopeLessThanOrEqual(ma25, ma25Prev, -0.0001);
			boolean isMa50Neg = isSlopeNegative(ma50, ma50Prev);
			boolean isMa75Neg = isSlopeNegative(ma75, ma75Prev);
			double ma5_2 = getPastMA(key, 5, 2);
			double ma10_2 = getPastMA(key, 10, 2);
			boolean narrowBeforeDown = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.001;
			boolean widenDown = ((ma10 - ma5) / ma10) >= PO_WIDEN_THRESHOLD;

			if (poDownShort && poDownLong && isMa5Down && isMa10Down && isMa25Down001 && isMa50Neg && isMa75Neg && narrowBeforeDown && widenDown) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 2, "【戦略2】PO下降中+初動急拡大(スクイーズ解放)");
			}
		}

		if (strategySettings.getOrDefault("4", false)) {
			boolean isPoShortExcept5 = (ma10 < ma25) && (ma25 < ma50) && (ma50 < ma75);
			boolean isAllDownExcept5 = isMa10Down && isMa25Down && isMa50Down && isMa75Down;
			if (isPoShortExcept5 && isAllDownExcept5) {
				boolean approached = false;
				for (int i = 1; i <= 5; i++) {
					double pMa5 = getPastMA(key, 5, i);
					double pMa10 = getPastMA(key, 10, i);
					if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD) { approached = true; break; }
				}
				if (approached && isMa5Down) {
					double lowest6 = getLowestLow(key, 6, 1);
					if (current.getClose() < lowest6) {
						return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 4, "【戦略4】PO(10-75)+10MA接近反発+安値更新");
					}
				}
			}
		}

		boolean confirmedDropTrig = isDownwardTrend(ma5, ma5Prev) && (current.getClose() < ma5);
		boolean wasMa5Up = getPastMA(key, 5, 1) > getPastMA(key, 5, 2);
		boolean is5and10Up = (wasMa5Up || isMa5Up) && isMa10Up;

		if (strategySettings.getOrDefault("5", false)) {
			boolean is25to75Down = isMa25Down && isMa50Down && isMa75Down;
			boolean orderStr5 = (ma10 < ma5) && (ma5 < ma25) && (ma25 < ma50) && (ma50 < ma75);
			if (is5and10Up && is25to75Down && orderStr5) {
				boolean approached = false;
				for (int i = 1; i <= 3; i++) {
					double pMa5 = getPastMA(key, 5, i);
					double pMa25 = getPastMA(key, 25, i);
					if (pMa25 > 0 && Math.abs(pMa5 - pMa25) / pMa25 <= NEARBY_MA_THRESHOLD) { approached = true; break; }
				}
				if (approached && confirmedDropTrig) {
					return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 5, "【戦略5】25MA接近+MA5反落");
				}
			}
		}

		if (strategySettings.getOrDefault("6", false)) {
			boolean is50and75Down = isMa50Down && isMa75Down;
			boolean orderStr6 = ((ma10 < ma5) || (ma25 < ma5)) && (ma5 < ma50) && (ma50 < ma75);
			if (is5and10Up && is50and75Down && orderStr6) {
				boolean approached = false;
				for (int i = 1; i <= 3; i++) {
					double pMa5 = getPastMA(key, 5, i);
					double pMa50 = getPastMA(key, 50, i);
					if (pMa50 > 0 && Math.abs(pMa5 - pMa50) / pMa50 <= NEARBY_MA_THRESHOLD) { approached = true; break; }
				}
				if (approached && confirmedDropTrig) {
					return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 6, "【戦略6】50MA接近+MA5反落");
				}
			}
		}

		boolean crossUp = getPastCandleClose(key, 2) <= getPastMA(key, 5, 2) && getPastCandleClose(key, 1) > getPastMA(key, 5, 1);
		boolean crossDown = getPastCandleClose(key, 2) >= getPastMA(key, 5, 2) && getPastCandleClose(key, 1) < getPastMA(key, 5, 1);

		if (strategySettings.getOrDefault("92", false)) {
			if (crossDown && ma10 <= ma10Prev && ma25 <= ma25Prev && ma50 <= ma50Prev) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 92, "【戦略9-2】確定足MA5下抜け ＋ MA10/25/50下向き同調（ドテン売り）");
			}
		}

		if (strategySettings.getOrDefault("9", false)) {
			if (crossDown) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 9, "【戦略9】確定足でMA5下抜け（ドテン売り）");
			}
		}

		if (strategySettings.getOrDefault("93", false)) {
			if (crossUp) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 93, "【戦略9-3】確定足MA5上抜けの逆張り（ドテン売り）");
			}
		}

		if (strategySettings.getOrDefault("94", false)) {
			double sma20 = getPastMA(key, 20, 0);
			double stdDev20 = getStdDev(key, 20, 0);
			double bandWidth = (sma20 > 0) ? (4 * stdDev20) / sma20 : 0;
			
			boolean isSqueeze = bandWidth > 0 && bandWidth <= BB_SQUEEZE_THRESHOLD;
			boolean isMa75Flat = Math.abs(ma75 - ma75Prev) / ma75Prev <= MA_FLAT_THRESHOLD;

			if (crossUp && (isSqueeze || isMa75Flat)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 94, "【戦略9-4】MA5上抜け ＋ レンジ相場(BB収縮orMA横ばい)で逆張り売り");
			}
		}

		if (strategySettings.getOrDefault("10", false)) {
			double c1_close = getPastCandleClose(key, 1);
			double c2_close = getPastCandleClose(key, 2);
			
			double c1_sma20 = getPastMA(key, 20, 1);
			double c1_std = getStdDev(key, 20, 1);
			double c1_upper = c1_sma20 + (2 * c1_std);
			
			double c2_sma20 = getPastMA(key, 20, 2);
			double c2_std = getStdDev(key, 20, 2);
			double c2_upper = c2_sma20 + (2 * c2_std);

			if (c1_close > c1_upper && c2_close <= c2_upper) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 10, "【戦略10】BB上限(+2σ)上抜け逆張り売り");
			}
		}

		if (strategySettings.getOrDefault("22", false)) {
			boolean poDownShort = (ma5 < ma10) && (ma10 < ma25);
			boolean poDownLong = (ma25 < ma50) && (ma50 < ma75);
			boolean isMa5Down22 = isSlopeLessThanOrEqual(ma5, ma5Prev, -MIN_SLOPE_THRESHOLD);
			boolean isMa10Down22 = isSlopeLessThanOrEqual(ma10, ma10Prev, -0.0005);
			boolean isMa25Down22 = isSlopeLessThanOrEqual(ma25, ma25Prev, -0.0005);
			double ma5_2 = getPastMA(key, 5, 2);
			double ma10_2 = getPastMA(key, 10, 2);
			boolean narrowBeforeDown22 = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.002;
			boolean widenDown22 = ((ma10 - ma5) / ma10) >= 0.002;
			boolean isMa50Neg22 = isSlopeNegative(ma50, ma50Prev);

			if (poDownShort && poDownLong && isMa5Down22 && isMa10Down22 && isMa25Down22 && narrowBeforeDown22 && widenDown22 && isMa50Neg22) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 22, "【戦略22】ブラッシュアップ版PO下降中");
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

		// 【修正】戦略9-2, 9-3, 9-4 すべてに金額ベースの損切りを適用
		double tradeSize = positionSizeMap.getOrDefault(key, 0.0);
		double pnl = (current.getClose() - entryPrice) * tradeSize; // 今の含み損益

		if (strategyId == 92 || strategyId == 93 || strategyId == 94) {
			if (pnl <= MAX_LOSS_JPY) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, 
					String.format("【戦略%d】損失額%.0f円超過による強制損切り決済", strategyId, pnl));
			}
		}

		if (strategyId == 9 || strategyId == 92) {
			double c1_close = getPastCandleClose(key, 1);
			double c1_ma5 = getPastMA(key, 5, 1);
			if (c1_close < c1_ma5) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "【戦略" + strategyId + "】確定足でMA5下抜け（ドテン決済）");
			return null;
		}

		if (strategyId == 93) {
			double c1_close = getPastCandleClose(key, 1);
			double c1_ma5 = getPastMA(key, 5, 1);
			if (c1_close > c1_ma5) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 93, "【戦略9-3】確定足でMA5上抜け（逆張りドテン決済）");
			return null;
		}

		if (strategyId == 94) {
			double c1_close = getPastCandleClose(key, 1);
			double c1_ma5 = getPastMA(key, 5, 1);
			if (c1_close > c1_ma5) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 94, "【戦略9-4】確定足でMA5上抜け（逆張りドテン決済）");
			return null;
		}

		if (strategyId == 10) {
			if (pnl <= STRATEGY10_MAX_LOSS_JPY) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 10, String.format("【戦略10】損失額%.0f円超過による強制損切り", pnl));
			}

			if (current.getTime() >= entryTime + candleSeconds) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 10, "【戦略10】次足終値での強制決済");
			}
			return null; 
		}

		if (strategyId == 3 || strategyId == 32) {
			double ma5 = getPastMA(key, 5, 0);
			double exitThreshold = ma5 * (1.0 - STRATEGY3_MA5_BUFFER);
			if (current.getClose() < exitThreshold) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "【戦略" + strategyId + "】MA5割れ(バッファ加味)決済");
			}
		}

		if ((strategyId == 2 || strategyId == 4 || strategyId == 42) && current.getTime() >= entryTime + (3 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "【戦略" + strategyId + "】時間経過強制決済(3本目)");
		}

		if (strategyId == 22) {
			double c2_close = getPastCandleClose(key, 2);
			double c2_open = getPastCandleOpen(key, 2);
			if (c2_close > 0 && c2_open > 0 && c2_close < c2_open && current.getTime() >= entryTime + (2 * candleSeconds)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 22, "【戦略22】陰線発生後の次足終値決済");
			}
		}

		double targetPct = getTargetPercentage(tf);
		if (current.getClose() >= entryPrice * (1.0 + targetPct)) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "【戦略" + strategyId + "】目標利益到達(TP)");
		if (current.getClose() <= entryPrice * (1.0 - targetPct)) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "【戦略" + strategyId + "】損切り到達(SL)");

		return null;
	}

	private SignalDecision checkExitShortSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0) return null;

		int strategyId = entryStrategyMap.getOrDefault(key, 1);
		long entryTime = entryCandleTimeMap.getOrDefault(key, 0L);
		long candleSeconds = tf.getSeconds();

		// 【修正】戦略9-2, 9-3, 9-4 すべてに金額ベースの損切りを適用
		double tradeSize = positionSizeMap.getOrDefault(key, 0.0);
		double pnl = (entryPrice - current.getClose()) * tradeSize; // ショートの含み損益

		if (strategyId == 92 || strategyId == 93 || strategyId == 94) {
			if (pnl <= MAX_LOSS_JPY) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, 
					String.format("【戦略%d】損失額%.0f円超過による強制損切り決済", strategyId, pnl));
			}
		}

		if (strategyId == 9 || strategyId == 92) {
			double c1_close = getPastCandleClose(key, 1);
			double c1_ma5 = getPastMA(key, 5, 1);
			if (c1_close > c1_ma5) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】確定足でMA5上抜け（ドテン決済）");
			return null;
		}

		if (strategyId == 93) {
			double c1_close = getPastCandleClose(key, 1);
			double c1_ma5 = getPastMA(key, 5, 1);
			if (c1_close < c1_ma5) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 93, "【戦略9-3】確定足でMA5下抜け（逆張りドテン決済）");
			return null;
		}

		if (strategyId == 94) {
			double c1_close = getPastCandleClose(key, 1);
			double c1_ma5 = getPastMA(key, 5, 1);
			if (c1_close < c1_ma5) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 94, "【戦略9-4】確定足でMA5下抜け（逆張りドテン決済）");
			return null;
		}

		if (strategyId == 10) {
			if (pnl <= STRATEGY10_MAX_LOSS_JPY) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 10, String.format("【戦略10】損失額%.0f円超過による強制損切り", pnl));
			}

			if (current.getTime() >= entryTime + candleSeconds) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 10, "【戦略10】次足終値での強制決済");
			}
			return null; 
		}

		if (strategyId == 3 || strategyId == 32) {
			double ma5 = getPastMA(key, 5, 0);
			double exitThreshold = ma5 * (1.0 + STRATEGY3_MA5_BUFFER);
			if (current.getClose() > exitThreshold) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】MA5上抜け(バッファ加味)決済");
			}
		}

		if ((strategyId == 2 || strategyId == 4 || strategyId == 42) && current.getTime() >= entryTime + (3 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】時間経過強制決済(3本目)");
		}

		if (strategyId == 22) {
			double c2_close = getPastCandleClose(key, 2);
			double c2_open = getPastCandleOpen(key, 2);
			if (c2_close > 0 && c2_open > 0 && c2_close > c2_open && current.getTime() >= entryTime + (2 * candleSeconds)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 22, "【戦略22】陽線発生後の次足終値決済");
			}
		}

		if ((strategyId == 5 || strategyId == 6 || strategyId == 52 || strategyId == 62) && current.getTime() >= entryTime + (2 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】時間経過強制決済(2本目)");
		}

		double targetPct = getTargetPercentage(tf);
		if (current.getClose() <= entryPrice * (1.0 - targetPct)) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】目標利益到達(TP)");
		if (current.getClose() >= entryPrice * (1.0 + targetPct)) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】損切り到達(SL)");

		return null;
	}

	private double getTargetPercentage(TimeFrame tf) {
		switch (tf.name()) {
		case "M1": return 0.0015;
		case "M5": return 0.003;
		case "M15": return 0.005;
		case "M30": return 0.008;
		case "H1": return 0.01;
		case "H4": return 0.02;
		case "D1": return 0.05;
		default: return 0.01;
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
	private boolean isSlopeGreaterThanOrEqual(double currentMa, double prevMa, double threshold) {
		if (prevMa <= 0) return false;
		return ((currentMa - prevMa) / prevMa) >= threshold;
	}
	private boolean isSlopeLessThanOrEqual(double currentMa, double prevMa, double threshold) {
		if (prevMa <= 0) return false;
		return ((currentMa - prevMa) / prevMa) <= threshold;
	}
	private boolean isSlopePositive(double currentMa, double prevMa) {
		if (prevMa <= 0) return false;
		return currentMa > prevMa;
	}
	private boolean isSlopeNegative(double currentMa, double prevMa) {
		if (prevMa <= 0) return false;
		return currentMa < prevMa;
	}

	private double calculateLotSize(double price) {
		if (price <= 0) return 0.001;
		double size = TARGET_TRADE_AMOUNT / price;
		return Math.round(size * 10000.0) / 10000.0;
	}

	private double getPastMA(String key, int period, int barsAgo) {
		if (barsAgo == 0) return calculateCurrentMA(key, period);
		List<CandleData> hist = historyMap.get(key);
		if (hist == null) return 0.0;
		int startIndex = hist.size() - barsAgo;
		if (startIndex - period + 1 < 0) return 0.0;
		double sum = 0;
		for (int i = 0; i < period; i++) sum += hist.get(startIndex - i).getClose();
		return sum / period;
	}

	private double getStdDev(String key, int period, int barsAgo) {
		if (barsAgo == 0) {
			double sma = calculateCurrentMA(key, period);
			List<CandleData> hist = historyMap.get(key);
			CandleData cur = currentCandleMap.get(key);
			if (hist == null || hist.size() < period - 1) return 0.0;
			double sumSq = Math.pow(cur.getClose() - sma, 2);
			for (int i = 1; i < period; i++) sumSq += Math.pow(hist.get(hist.size() - i).getClose() - sma, 2);
			return Math.sqrt(sumSq / period);
		} else {
			double sma = getPastMA(key, period, barsAgo);
			List<CandleData> hist = historyMap.get(key);
			if (hist == null || hist.size() < period + barsAgo) return 0.0;
			int startIndex = hist.size() - barsAgo;
			double sumSq = 0;
			for (int i = 0; i < period; i++) sumSq += Math.pow(hist.get(startIndex - i).getClose() - sma, 2);
			return Math.sqrt(sumSq / period);
		}
	}

	private double getRSI(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo + 1) return 50.0;

		double gain = 0.0, loss = 0.0;
		if (barsAgo == 0) {
			CandleData cur = currentCandleMap.get(key);
			double change = cur.getClose() - hist.get(hist.size() - 1).getClose();
			if (change > 0) gain += change; else loss -= change;
			for (int i = 1; i < period; i++) {
				double diff = hist.get(hist.size() - i).getClose() - hist.get(hist.size() - i - 1).getClose();
				if (diff > 0) gain += diff; else loss -= diff;
			}
		} else {
			int startIndex = hist.size() - barsAgo;
			for (int i = 0; i < period; i++) {
				double diff = hist.get(startIndex - i).getClose() - hist.get(startIndex - i - 1).getClose();
				if (diff > 0) gain += diff; else loss -= diff;
			}
		}

		gain /= period; loss /= period;
		if (loss == 0) return 100.0;
		if (gain == 0) return 0.0;
		return 100.0 - (100.0 / (1 + (gain / loss)));
	}

	private void executeTrade(Symbol symbol, TimeFrame tf, SignalDecision decision, CandleData candle) {
		String key = symbol.name() + "_" + tf.name();
		String currentPos = positionMap.getOrDefault(key, "NONE");

		boolean isNewEntry = "NONE".equals(currentPos);
		
		if (isNewEntry && decision.getStrategyId() != 9 && decision.getStrategyId() != 92 && decision.getStrategyId() != 93 && decision.getStrategyId() != 94 && lastOrderTimeMap.get(key).equals(candle.getTime())) {
			return;
		}

		String newPos = currentPos;
		String actionType = "";
		double tradeSize = 0.001;

		if (isNewEntry) {
			tradeSize = calculateLotSize(candle.getClose());
			if (decision.getType() == RealtimeUpdateDto.SignalType.BUY) {
				newPos = "LONG"; actionType = "🟢 [LONG] " + decision.getReason();
			} else {
				newPos = "SHORT"; actionType = "🔴 [SHORT] " + decision.getReason();
			}
		} else {
			tradeSize = positionSizeMap.getOrDefault(key, 0.001);
			if ("LONG".equals(currentPos) && decision.getType() == RealtimeUpdateDto.SignalType.SELL) {
				newPos = "NONE"; actionType = "✅ [LONG決済] " + decision.getReason();
			} else if ("SHORT".equals(currentPos) && decision.getType() == RealtimeUpdateDto.SignalType.BUY) {
				newPos = "NONE"; actionType = "✅ [SHORT決済] " + decision.getReason();
			} else {
				return;
			}
		}

		TradeLog logTrade = new TradeLog();
		logTrade.setTime(System.currentTimeMillis() / 1000);
		logTrade.setSymbol(symbol.name());
		logTrade.setTimeframe(tf.name());
		logTrade.setSide(decision.getType().name());
		logTrade.setPrice(candle.getClose());
		logTrade.setSize(tradeSize);
		logTrade.setMessage(actionType);
		logTrade.setStrategy(decision.getStrategyId());

		tradeLogRepository.save(logTrade);
		messagingTemplate.convertAndSend("/topic/trades", logTrade);

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
		TradeLog systemLog = new TradeLog();
		systemLog.setTime(System.currentTimeMillis() / 1000);
		systemLog.setSymbol("SYSTEM");
		systemLog.setTimeframe("-");
		systemLog.setSide(status);
		systemLog.setPrice(0.0);
		systemLog.setSize(0.0);
		systemLog.setMessage(message);
		systemLog.setStrategy(0);
		tradeLogRepository.save(systemLog);
		messagingTemplate.convertAndSend("/topic/trades", systemLog);
	}

	private double getHighestHigh(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo) return Double.MAX_VALUE;
		double highest = 0;
		int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) highest = Math.max(highest, hist.get(endIndex - i).getHigh());
		return highest;
	}

	private double getLowestLow(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo) return 0.0;
		double lowest = Double.MAX_VALUE;
		int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) lowest = Math.min(lowest, hist.get(endIndex - i).getLow());
		return lowest;
	}

	private double getPastCandleClose(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < barsAgo) return 0.0;
		return hist.get(hist.size() - barsAgo).getClose();
	}

	private double getPastCandleOpen(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < barsAgo) return 0.0;
		return hist.get(hist.size() - barsAgo).getOpen();
	}
}