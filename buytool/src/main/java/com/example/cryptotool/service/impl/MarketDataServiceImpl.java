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

	// MySQLに保存するためのリモコン
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

	private final double TARGET_TRADE_AMOUNT = 1500000.0;

	private final double MIN_SLOPE_THRESHOLD = 0.0003;
	private final double NEARBY_MA_THRESHOLD = 0.005;
	private final double PO_WIDEN_THRESHOLD = 0.002;

	private final double SQUEEZE_THRESHOLD = 0.001;
	private final double DEVIATION_THRESHOLD = 0.01;

	// 追加: 緩和版（-2シリーズ）用の閾値
	private final double MIN_SLOPE_THRESHOLD_RELAXED = 0.0001;
	private final double NEARBY_MA_THRESHOLD_RELAXED = 0.01;

	// 追加: 戦略8（トレンドチャネル）用の閾値
	private final double STRATEGY8_APPROACH_THRESHOLD = 0.001;
	private final int STRATEGY8_LOOKBACK = 60;
	// 追加: 戦略8用の許容誤差と決済バッファー
	private final double STRATEGY8_TREND_TOLERANCE = 0.001; // トレンド線に対する各頂点の許容誤差(0.1%)
	private final double STRATEGY8_MA5_BUFFER = 0.002; // 決済時のMA5抜けバッファー(0.2%)

	// 追加: 戦略3用の決済バッファー
	private final double STRATEGY3_MA5_BUFFER = 0.002; // 戦略3/32の決済時MA5抜けバッファー(0.2%)

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

						if (fetched == null)
							fetched = new ArrayList<>();

						if (!fetched.isEmpty()) {
							CandleData last = fetched.remove(fetched.size() - 1);
							currentCandleMap.put(key, last);
						}
						historyMap.put(key, fetched);

						// ★★★ ここから記憶の復元処理 ★★★
						Optional<TradeLog> optLatestLog = tradeLogRepository
								.findFirstBySymbolAndTimeframeOrderByTimeDesc(s.name(), tf.name());
						boolean hasPosition = false;

						if (optLatestLog.isPresent()) {
							TradeLog latestLog = optLatestLog.get();
							// 最新のログが「決済」でも「SYSTEM」でもない場合、それはエントリー状態を意味する
							if (!latestLog.getMessage().contains("決済") && !latestLog.getMessage().contains("SYSTEM")) {
								String restoredPos = latestLog.getMessage().contains("[LONG]") ? "LONG" : "SHORT";
								positionMap.put(key, restoredPos);
								entryPriceMap.put(key, latestLog.getPrice());
								positionSizeMap.put(key, latestLog.getSize());
								entryStrategyMap.put(key, latestLog.getStrategy());

								long candleStart = (latestLog.getTime() / tf.getSeconds()) * tf.getSeconds();
								entryCandleTimeMap.put(key, candleStart);
								hasPosition = true;

								log.info("🔄 [記憶復元] {} の {} ポジション (価格: {}) をDBから復元し、監視を再開します。", key, restoredPos,
										latestLog.getPrice());
							}
						}

						if (!hasPosition) {
							positionMap.put(key, "NONE");
							entryPriceMap.put(key, 0.0);
							positionSizeMap.put(key, 0.0);
							entryStrategyMap.put(key, 0);
							entryCandleTimeMap.put(key, 0L);
						}
						// ★★★ 記憶の復元処理ここまで ★★★

						lastOrderTimeMap.put(key, 0L);

						// ポジションを復元した場合は、決済漏れを防ぐために自動で監視ONにする
						monitorSettings.put(key, hasPosition);

						Thread.sleep(1000);
					}
				}
				isSystemReady = true;
				addSystemLog("SYSTEM READY", "全データの準備とポジション復元が完了し、監視態勢に入りました。");
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

	public Map<String, Boolean> getMonitorSettings() {
		return monitorSettings;
	}

	public List<TradeLog> getTradeHistory() {
		return tradeLogRepository.findTop100ByOrderByTimeDesc();
	}

	@Override
	public List<TradeLog> getAllTradeHistory() {
		return tradeLogRepository.findAllByOrderByTimeDesc();
	}

	// ★追加: 画面のチャート初期描画時に、そのチャート用のマーカー履歴を渡すためのメソッド
	@Override
	public List<TradeLog> getTradeLogsForChart(Symbol symbol, TimeFrame tf) {
		// 指定された通貨・時間足の取引履歴を古い順（時間昇順）で取得する
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
		if (current != null)
			candles.add(current);

		return ChartInitResponse.builder().candles(candles)
				.ma5(calculateHistoricalMA(candles, 5)).ma10(calculateHistoricalMA(candles, 10))
				.ma25(calculateHistoricalMA(candles, 25)).ma50(calculateHistoricalMA(candles, 50))
				.ma75(calculateHistoricalMA(candles, 75)).ma100(calculateHistoricalMA(candles, 100)).build();
	}

	@Override
	public void processRealtimeTick(TickData tick) {
		if (!isSystemReady)
			return;
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
						.currentMa5(calculateCurrentMA(key, 5)).currentMa10(calculateCurrentMA(key, 10))
						.currentMa25(calculateCurrentMA(key, 25))
						.currentMa50(calculateCurrentMA(key, 50)).currentMa75(calculateCurrentMA(key, 75))
						.currentMa100(calculateCurrentMA(key, 100))
						.supportLine(getTrendLineValue(key, false))
						.resistanceLine(getTrendLineValue(key, true))
						.signal(decision != null ? decision.getType() : RealtimeUpdateDto.SignalType.NONE).build());
	}

	private SignalDecision checkSignal(TimeFrame tf, String key, CandleData current) {
		if (current == null)
			return null;

		String currentPosition = positionMap.getOrDefault(key, "NONE");

		if ("NONE".equals(currentPosition)) {
			SignalDecision buySignal = checkBuySignal(key, current);
			if (buySignal != null)
				return buySignal;

			SignalDecision shortSignal = checkShortSignal(key, current);
			if (shortSignal != null)
				return shortSignal;
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

		if (ma5 == 0)
			return null;

		boolean isMa5Up = isUpwardTrend(ma5, ma5Prev);
		boolean isMa10Up = isUpwardTrend(ma10, ma10Prev);
		boolean isMa25Up = isUpwardTrend(ma25, ma25Prev);
		boolean isMa50Down = isDownwardTrend(ma50, ma50Prev);
		boolean isMa50Up = isUpwardTrend(ma50, ma50Prev);
		boolean isMa75Up = isUpwardTrend(ma75, ma75Prev);

		// 【戦略1】
		boolean cross10 = (ma5Prev <= ma10Prev) && (ma5 > ma10);
		if (cross10 && isMa5Up && isMa10Up && isMa25Up) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 1, "【戦略1】GC(5&10)+25MA同調");
		}
		boolean cross25 = (ma5Prev <= ma25Prev) && (ma5 > ma25);
		boolean ma50Nearby = (Math.abs(ma50 - current.getClose()) / current.getClose()) < NEARBY_MA_THRESHOLD;
		if (cross25 && isMa5Up && isMa25Up && isMa10Up && !(ma50Nearby && isMa50Down)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 1, "【戦略1】GC(5&25)+50MAレジスタンス回避");
		}

		// 【戦略2改】
		boolean poUpShort = (ma5 > ma10) && (ma10 > ma25);
		boolean poUpLong = (ma25 > ma50) && (ma50 > ma75);
		boolean isMa25Up001 = isSlopeGreaterThanOrEqual(ma25, ma25Prev, 0.0001);
		boolean isMa50Pos = isSlopePositive(ma50, ma50Prev);
		boolean isMa75Pos = isSlopePositive(ma75, ma75Prev);

		double ma5_2 = getPastMA(key, 5, 2);
		double ma10_2 = getPastMA(key, 10, 2);
		boolean narrowBeforeUp = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.001;
		boolean widenUp = ((ma5 - ma10) / ma10) >= PO_WIDEN_THRESHOLD;

		if (poUpShort && poUpLong && isMa5Up && isMa10Up && isMa25Up001 && isMa50Pos && isMa75Pos && narrowBeforeUp
				&& widenUp) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 2, "【戦略2】PO上昇+初動急拡大(スクイーズ解放)");
		}

		// 【戦略3改】
		boolean isMa5Down = isDownwardTrend(ma5, ma5Prev);
		boolean shortTermDown = (ma5 < ma10) && isMa5Down;

		// 直近3本+現在のMA50/75接近確認 (0.2%バッファ)
		double prevLow3 = getLowestLow(key, 3, 1);
		double lowest3 = (prevLow3 > 0) ? Math.min(prevLow3, current.getLow()) : current.getLow();
		boolean approach75 = (lowest3 <= ma75 * 1.002) && (current.getClose() > ma75);
		boolean approach50 = (lowest3 <= ma50 * 1.002) && (current.getClose() > ma50);

		// 反発の確定（MA5が上向き、かつ現在の終値がMA5を上抜けている）
		boolean confirmedRebound = isUpwardTrend(ma5, ma5Prev) && (current.getClose() > ma5);

		// 長期線の傾き（通常版：50MA=0.02%, 75MA=0.01%）
		boolean isMa50UpStrict3 = isSlopeGreaterThanOrEqual(ma50, ma50Prev, 0.0002);
		boolean isMa75UpStrict3 = isSlopeGreaterThanOrEqual(ma75, ma75Prev, 0.0001);

		if (shortTermDown && confirmedRebound) {
			if (isMa50UpStrict3 && approach50) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 3, "【戦略3】50MA(0.02%↑)サポート接近+反発");
			}
			if (isMa75UpStrict3 && approach75) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 3, "【戦略3】75MA(0.01%↑)サポート接近+反発");
			}
		}

		// 【戦略4】買い（100MAの縛りを削除）
		boolean isPoBuyExcept5 = (ma10 > ma25) && (ma25 > ma50) && (ma50 > ma75);
		boolean isAllUpExcept5 = isMa10Up && isMa25Up && isMa50Up && isMa75Up;
		if (isPoBuyExcept5 && isAllUpExcept5) {
			boolean approached = false;
			for (int i = 1; i <= 5; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa10 = getPastMA(key, 10, i);
				if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD) {
					approached = true;
					break;
				}
			}
			if (approached && isMa5Up) {
				double highest6 = getHighestHigh(key, 6, 1);
				if (current.getClose() > highest6) {
					return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 4, "【戦略4】PO(10-75)+10MA接近反発+高値更新");
				}
			}
		}

		// ---------------------------------------------------------------------
		// ★ 新規: 緩和版ロジック（X-2 シリーズ）
		// ---------------------------------------------------------------------

		boolean isMa5UpRel = isUpwardTrendRelaxed(ma5, ma5Prev);
		boolean isMa10UpRel = isUpwardTrendRelaxed(ma10, ma10Prev);
		boolean isMa25UpRel = isUpwardTrendRelaxed(ma25, ma25Prev);

		// 【戦略1-2】緩和版GC
		if (cross10 && isMa5UpRel && isMa10UpRel && isMa25UpRel) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 12, "【戦略12】緩和版GC(5&10)+25MA同調");
		}
		if (cross25 && isMa5UpRel && isMa25UpRel && isMa10UpRel) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 12, "【戦略12】緩和版GC(5&25)+MA10同調");
		}

		// 【戦略2-2】ブラッシュアップ版PO
		boolean isMa5Up22 = isSlopeGreaterThanOrEqual(ma5, ma5Prev, MIN_SLOPE_THRESHOLD); // 戦略2と同様の0.03%
		boolean isMa10Up22 = isSlopeGreaterThanOrEqual(ma10, ma10Prev, 0.0005); // 0.05%
		boolean isMa25Up22 = isSlopeGreaterThanOrEqual(ma25, ma25Prev, 0.0005); // 0.05%
		boolean narrowBeforeUp22 = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.002; // スクイーズ0.2%以下
		boolean widenUp22 = ((ma5 - ma10) / ma10) >= 0.002; // エクスパンション0.2%以上
		boolean isMa50Pos22 = isSlopePositive(ma50, ma50Prev);

		if (poUpShort && poUpLong && isMa5Up22 && isMa10Up22 && isMa25Up22 && narrowBeforeUp22 && widenUp22
				&& isMa50Pos22) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 22, "【戦略22】ブラッシュアップ版PO上昇中");
		}

		// 【戦略3-2】緩和版反発
		boolean confirmedReboundRel = isUpwardTrendRelaxed(ma5, ma5Prev) && (current.getClose() > ma5);
		boolean isMa50Pos3 = isSlopePositive(ma50, ma50Prev);
		boolean isMa75Pos3 = isSlopePositive(ma75, ma75Prev);

		if (shortTermDown && confirmedReboundRel) {
			if (isMa50Pos3 && approach50) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 32, "【戦略32】緩和版50MAサポート接近+反発");
			}
			if (isMa75Pos3 && approach75) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 32, "【戦略32】緩和版75MAサポート接近+反発");
			}
		}

		// 【戦略4-2】緩和版PO接近 (上位足にも正しく緩和傾きを適用)
		boolean isMa50UpRel4 = isSlopeGreaterThanOrEqual(ma50, ma50Prev, MIN_SLOPE_THRESHOLD_RELAXED);
		boolean isMa75UpRel4 = isSlopeGreaterThanOrEqual(ma75, ma75Prev, MIN_SLOPE_THRESHOLD_RELAXED);
		boolean isAllUpExcept5Rel = isMa10UpRel && isMa25UpRel && isMa50UpRel4 && isMa75UpRel4;

		if (isPoBuyExcept5 && isAllUpExcept5Rel) {
			boolean approachedRel = false;
			for (int i = 1; i <= 5; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa10 = getPastMA(key, 10, i);
				if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD_RELAXED) {
					approachedRel = true;
					break;
				}
			}
			if (approachedRel && isMa5UpRel) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 42, "【戦略42】緩和版PO(10-75)接近反発");
			}
		}

		// 【戦略8】トレンドチャネル（支持線）反発
		Double supportLine = getTrendLineValue(key, false);
		if (supportLine != null) {
			boolean approachedLine = current.getLow() <= supportLine * (1.0 + STRATEGY8_APPROACH_THRESHOLD);
			boolean ma5CrossUp = (getPastCandleClose(key, 1) <= ma5Prev) && (current.getClose() > ma5);
			if (approachedLine && ma5CrossUp) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 8, "【戦略8】トレンド支持線接近+MA5上抜け");
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

		if (ma5 == 0)
			return null;

		boolean isMa5Down = isDownwardTrend(ma5, ma5Prev);
		boolean isMa10Down = isDownwardTrend(ma10, ma10Prev);
		boolean isMa25Down = isDownwardTrend(ma25, ma25Prev);
		boolean isMa50Down = isDownwardTrend(ma50, ma50Prev);
		boolean isMa75Down = isDownwardTrend(ma75, ma75Prev);

		boolean isMa5Up = isUpwardTrend(ma5, ma5Prev);
		boolean isMa10Up = isUpwardTrend(ma10, ma10Prev);
		boolean isMa25Up = isUpwardTrend(ma25, ma25Prev);
		boolean isMa50Up = isUpwardTrend(ma50, ma50Prev);

		// 【戦略1】
		boolean cross10 = (ma5Prev >= ma10Prev) && (ma5 < ma10);
		if (cross10 && isMa5Down && isMa10Down && isMa25Down) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 1, "【戦略1】DC(5&10)+25MA同調");
		}
		boolean cross25 = (ma5Prev >= ma25Prev) && (ma5 < ma25);
		boolean ma50Nearby = (Math.abs(ma50 - current.getClose()) / current.getClose()) < NEARBY_MA_THRESHOLD;
		if (cross25 && isMa5Down && isMa25Down && isMa10Down && !(ma50Nearby && isMa50Up)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 1, "【戦略1】DC(5&25)+50MAサポート回避");
		}

		// 【戦略2改】
		boolean poDownShort = (ma5 < ma10) && (ma10 < ma25);
		boolean poDownLong = (ma25 < ma50) && (ma50 < ma75);
		boolean isMa25Down001 = isSlopeLessThanOrEqual(ma25, ma25Prev, -0.0001);
		boolean isMa50Neg = isSlopeNegative(ma50, ma50Prev);
		boolean isMa75Neg = isSlopeNegative(ma75, ma75Prev);

		double ma5_2 = getPastMA(key, 5, 2);
		double ma10_2 = getPastMA(key, 10, 2);
		boolean narrowBeforeDown = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.001;
		boolean widenDown = ((ma10 - ma5) / ma10) >= PO_WIDEN_THRESHOLD;

		if (poDownShort && poDownLong && isMa5Down && isMa10Down && isMa25Down001 && isMa50Neg && isMa75Neg
				&& narrowBeforeDown && widenDown) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 2, "【戦略2】PO下降中+初動急拡大(スクイーズ解放)");
		}

		// 【戦略3改】
		boolean shortTermUp = (ma5 > ma10) && isMa5Up;

		// 直近3本+現在のMA50/75接近確認 (0.2%バッファ)
		double prevHigh3 = getHighestHigh(key, 3, 1);
		double highest3 = (prevHigh3 < Double.MAX_VALUE) ? Math.max(prevHigh3, current.getHigh()) : current.getHigh();
		boolean approach75Short = (highest3 >= ma75 * 0.998) && (current.getClose() < ma75);
		boolean approach50Short = (highest3 >= ma50 * 0.998) && (current.getClose() < ma50);

		// 反落の確定（MA5が下向き、かつ現在の終値がMA5を下抜けている）
		boolean confirmedDrop = isDownwardTrend(ma5, ma5Prev) && (current.getClose() < ma5);

		// 長期線の傾き（通常版：50MA=-0.02%, 75MA=-0.01%）
		boolean isMa50DownStrict3 = isSlopeLessThanOrEqual(ma50, ma50Prev, -0.0002);
		boolean isMa75DownStrict3 = isSlopeLessThanOrEqual(ma75, ma75Prev, -0.0001);

		if (shortTermUp && confirmedDrop) {
			if (isMa50DownStrict3 && approach50Short) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 3, "【戦略3】50MA(-0.02%↓)レジスタンス接近+反落");
			}
			if (isMa75DownStrict3 && approach75Short) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 3, "【戦略3】75MA(-0.01%↓)レジスタンス接近+反落");
			}
		}

		// 【戦略4】売り（100MA縛りを削除）
		boolean isPoShortExcept5 = (ma10 < ma25) && (ma25 < ma50) && (ma50 < ma75);
		boolean isAllDownExcept5 = isMa10Down && isMa25Down && isMa50Down && isMa75Down;
		if (isPoShortExcept5 && isAllDownExcept5) {
			boolean approached = false;
			for (int i = 1; i <= 5; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa10 = getPastMA(key, 10, i);
				if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD) {
					approached = true;
					break;
				}
			}
			if (approached && isMa5Down) {
				double lowest6 = getLowestLow(key, 6, 1);
				if (current.getClose() < lowest6) {
					return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 4, "【戦略4】PO(10-75)+10MA接近反発+安値更新");
				}
			}
		}

		boolean wasMa5Up = getPastMA(key, 5, 1) > getPastMA(key, 5, 2);
		boolean is5and10Up = (wasMa5Up || isMa5Up) && isMa10Up;

		// 【戦略5】売り（包み足縛りを解除・MA5反落トリガーへ）
		boolean is25to75Down = isMa25Down && isMa50Down && isMa75Down;
		boolean orderStr5 = (ma10 < ma5) && (ma5 < ma25) && (ma25 < ma50) && (ma50 < ma75);
		if (is5and10Up && is25to75Down && orderStr5) {
			boolean approached = false;
			for (int i = 1; i <= 3; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa25 = getPastMA(key, 25, i);
				if (pMa25 > 0 && Math.abs(pMa5 - pMa25) / pMa25 <= NEARBY_MA_THRESHOLD) {
					approached = true;
					break;
				}
			}
			if (approached && confirmedDrop) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 5, "【戦略5】25MA接近+MA5反落");
			}
		}

		// 【戦略6】売り（包み足縛りを解除・MA5反落トリガーへ）
		boolean is50and75Down = isMa50Down && isMa75Down;
		boolean orderStr6 = ((ma10 < ma5) || (ma25 < ma5)) && (ma5 < ma50) && (ma50 < ma75);
		if (is5and10Up && is50and75Down && orderStr6) {
			boolean approached = false;
			for (int i = 1; i <= 3; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa50 = getPastMA(key, 50, i);
				if (pMa50 > 0 && Math.abs(pMa5 - pMa50) / pMa50 <= NEARBY_MA_THRESHOLD) {
					approached = true;
					break;
				}
			}
			if (approached && confirmedDrop) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 6, "【戦略6】50MA接近+MA5反落");
			}
		}

		// ---------------------------------------------------------------------
		// ★ 新規: 緩和版ロジック（X-2 シリーズ）
		// ---------------------------------------------------------------------

		boolean isMa5DownRel = isDownwardTrendRelaxed(ma5, ma5Prev);
		boolean isMa10DownRel = isDownwardTrendRelaxed(ma10, ma10Prev);
		boolean isMa25DownRel = isDownwardTrendRelaxed(ma25, ma25Prev);

		// 【戦略1-2】緩和版DC
		if (cross10 && isMa5DownRel && isMa10DownRel && isMa25DownRel) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 12, "【戦略12】緩和版DC(5&10)+25MA同調");
		}

		// 【戦略2-2】ブラッシュアップ版PO
		boolean isMa5Down22 = isSlopeLessThanOrEqual(ma5, ma5Prev, -MIN_SLOPE_THRESHOLD); // 戦略2と同様の0.03%
		boolean isMa10Down22 = isSlopeLessThanOrEqual(ma10, ma10Prev, -0.0005); // 0.05%
		boolean isMa25Down22 = isSlopeLessThanOrEqual(ma25, ma25Prev, -0.0005); // 0.05%
		boolean narrowBeforeDown22 = ma10_2 > 0 && (Math.abs(ma5_2 - ma10_2) / ma10_2) <= 0.002; // スクイーズ0.2%以下
		boolean widenDown22 = ((ma10 - ma5) / ma10) >= 0.002; // エクスパンション0.2%以上
		boolean isMa50Neg22 = isSlopeNegative(ma50, ma50Prev);

		if (poDownShort && poDownLong && isMa5Down22 && isMa10Down22 && isMa25Down22 && narrowBeforeDown22
				&& widenDown22 && isMa50Neg22) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 22, "【戦略22】ブラッシュアップ版PO下降中");
		}

		// 【戦略3-2】緩和版反発
		boolean confirmedDropRel = isDownwardTrendRelaxed(ma5, ma5Prev) && (current.getClose() < ma5);
		boolean isMa50Neg3 = isSlopeNegative(ma50, ma50Prev);
		boolean isMa75Neg3 = isSlopeNegative(ma75, ma75Prev);

		if (shortTermUp && confirmedDropRel) {
			if (isMa50Neg3 && approach50Short) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 32, "【戦略32】緩和版50MAレジスタンス接近+反落");
			}
			if (isMa75Neg3 && approach75Short) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 32, "【戦略32】緩和版75MAレジスタンス接近+反落");
			}
		}

		// 【戦略4-2】緩和版PO接近 (上位足にも正しく緩和傾きを適用)
		boolean isMa50DownRel4 = isSlopeLessThanOrEqual(ma50, ma50Prev, -MIN_SLOPE_THRESHOLD_RELAXED);
		boolean isMa75DownRel4 = isSlopeLessThanOrEqual(ma75, ma75Prev, -MIN_SLOPE_THRESHOLD_RELAXED);
		boolean isAllDownExcept5Rel = isMa10DownRel && isMa25DownRel && isMa50DownRel4 && isMa75DownRel4;

		if (isPoShortExcept5 && isAllDownExcept5Rel) {
			boolean approachedRel = false;
			for (int i = 1; i <= 5; i++) {
				double pMa5 = getPastMA(key, 5, i);
				double pMa10 = getPastMA(key, 10, i);
				if (pMa10 > 0 && Math.abs(pMa5 - pMa10) / pMa10 <= NEARBY_MA_THRESHOLD_RELAXED) {
					approachedRel = true;
					break;
				}
			}
			if (approachedRel && isMa5DownRel) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 42, "【戦略42】緩和版PO(10-75)接近反落");
			}
		}

		// 【戦略5-2 / 6-2】緩和版MA5反落
		boolean is25to75DownRel = isMa25DownRel && isMa50DownRel4 && isMa75DownRel4;
		boolean is50and75DownRel = isMa50DownRel4 && isMa75DownRel4;

		if (is5and10Up && (is25to75DownRel || is50and75DownRel)) {
			boolean approachedMaRel = false;
			double targetMa = is25to75DownRel ? getPastMA(key, 25, 0) : getPastMA(key, 50, 0);
			if (targetMa > 0 && Math.abs(ma5 - targetMa) / targetMa <= NEARBY_MA_THRESHOLD_RELAXED) {
				approachedMaRel = true;
			}
			if (approachedMaRel && confirmedDropRel) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, is25to75DownRel ? 52 : 62,
						"【戦略" + (is25to75DownRel ? 52 : 62) + "】緩和版MA接近+MA5反落");
			}
		}

		// 【戦略8】トレンドチャネル（抵抗線）反発
		Double resistanceLine = getTrendLineValue(key, true);
		if (resistanceLine != null) {
			boolean approachedLine = current.getHigh() >= resistanceLine * (1.0 - STRATEGY8_APPROACH_THRESHOLD);
			boolean ma5CrossDown = (getPastCandleClose(key, 1) >= ma5Prev) && (current.getClose() < ma5);
			if (approachedLine && ma5CrossDown) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 8, "【戦略8】トレンド抵抗線接近+MA5下抜け");
			}
		}

		return null;
	}

	private SignalDecision checkExitLongSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0)
			return null;

		int strategyId = entryStrategyMap.getOrDefault(key, 1);
		long entryTime = entryCandleTimeMap.getOrDefault(key, 0L);
		long candleSeconds = tf.getSeconds();

		if (strategyId == 8) {
			Double targetLine = getTrendLineValue(key, true);
			double ma5 = getPastMA(key, 5, 0);

			// MA5からバッファー分（0.2%）さらに下回ったラインを決済ポイントとする
			double exitThreshold = ma5 * (1.0 - STRATEGY8_MA5_BUFFER);

			if ((targetLine != null && current.getHigh() >= targetLine * 0.9995)
					|| (current.getClose() < exitThreshold)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 8, "【戦略8】ターゲット到達orMA5割れ(バッファ加味)決済");
			}
		}

		// 戦略3, 32の利確（0.2%バッファでMA5割れ）
		if (strategyId == 3 || strategyId == 32) {
			double ma5 = getPastMA(key, 5, 0);
			double exitThreshold = ma5 * (1.0 - STRATEGY3_MA5_BUFFER);
			// エントリー時すでにMA5下にいる場合は除外され、固定TP/SLへ委ねる
			if (current.getClose() < exitThreshold) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId,
						"【戦略" + strategyId + "】MA5割れ(バッファ加味)決済");
			}
		}

		if ((strategyId == 2 || strategyId == 4 || strategyId == 42)
				&& current.getTime() >= entryTime + (3 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId,
					"【戦略" + strategyId + "】時間経過強制決済(3本目)");
		}

		// 戦略2-2用の決済ロジック: 陰線が発生した後の次足終値で決済
		if (strategyId == 22) {
			double c2_close = getPastCandleClose(key, 2);
			double c2_open = getPastCandleOpen(key, 2);
			if (c2_close > 0 && c2_open > 0 && c2_close < c2_open
					&& current.getTime() >= entryTime + (2 * candleSeconds)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 22, "【戦略22】陰線発生後の次足終値決済");
			}
		}

		double targetPct = getTargetPercentage(tf);
		if (current.getClose() >= entryPrice * (1.0 + targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId,
					"【戦略" + strategyId + "】目標利益到達(TP)");
		}
		if (current.getClose() <= entryPrice * (1.0 - targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, "【戦略" + strategyId + "】損切り到達(SL)");
		}

		return null;
	}

	private SignalDecision checkExitShortSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0)
			return null;

		int strategyId = entryStrategyMap.getOrDefault(key, 1);
		long entryTime = entryCandleTimeMap.getOrDefault(key, 0L);
		long candleSeconds = tf.getSeconds();

		if (strategyId == 8) {
			Double targetLine = getTrendLineValue(key, false);
			double ma5 = getPastMA(key, 5, 0);

			// MA5からバッファー分（0.2%）さらに上回ったラインを決済ポイントとする
			double exitThreshold = ma5 * (1.0 + STRATEGY8_MA5_BUFFER);

			if ((targetLine != null && current.getLow() <= targetLine * 1.0005)
					|| (current.getClose() > exitThreshold)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 8, "【戦略8】ターゲット到達orMA5上抜け(バッファ加味)決済");
			}
		}

		// 戦略3, 32の利確（0.2%バッファでMA5上抜け）
		if (strategyId == 3 || strategyId == 32) {
			double ma5 = getPastMA(key, 5, 0);
			double exitThreshold = ma5 * (1.0 + STRATEGY3_MA5_BUFFER);
			if (current.getClose() > exitThreshold) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId,
						"【戦略" + strategyId + "】MA5上抜け(バッファ加味)決済");
			}
		}

		if ((strategyId == 2 || strategyId == 4 || strategyId == 42)
				&& current.getTime() >= entryTime + (3 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId,
					"【戦略" + strategyId + "】時間経過強制決済(3本目)");
		}

		// 戦略2-2用の決済ロジック: 陽線が発生した後の次足終値で決済
		if (strategyId == 22) {
			double c2_close = getPastCandleClose(key, 2);
			double c2_open = getPastCandleOpen(key, 2);
			if (c2_close > 0 && c2_open > 0 && c2_close > c2_open
					&& current.getTime() >= entryTime + (2 * candleSeconds)) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 22, "【戦略22】陽線発生後の次足終値決済");
			}
		}

		if ((strategyId == 5 || strategyId == 6 || strategyId == 52 || strategyId == 62)
				&& current.getTime() >= entryTime + (2 * candleSeconds)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId,
					"【戦略" + strategyId + "】時間経過強制決済(2本目)");
		}

		double targetPct = getTargetPercentage(tf);
		if (current.getClose() <= entryPrice * (1.0 - targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】目標利益到達(TP)");
		}
		if (current.getClose() >= entryPrice * (1.0 + targetPct)) {
			return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, "【戦略" + strategyId + "】損切り到達(SL)");
		}

		return null;
	}

	private double getTargetPercentage(TimeFrame tf) {
		switch (tf.name()) {
		case "M1":
			return 0.0015;
		case "M5":
			return 0.003;
		case "M15":
			return 0.005;
		case "M30":
			return 0.008;
		case "H1":
			return 0.01;
		case "H4":
			return 0.02;
		case "D1":
			return 0.05;
		default:
			return 0.01;
		}
	}

	private boolean isUpwardTrend(double currentMa, double prevMa) {
		if (prevMa <= 0)
			return false;
		return ((currentMa - prevMa) / prevMa) >= MIN_SLOPE_THRESHOLD;
	}

	// 緩和版
	private boolean isUpwardTrendRelaxed(double currentMa, double prevMa) {
		if (prevMa <= 0)
			return false;
		return ((currentMa - prevMa) / prevMa) >= MIN_SLOPE_THRESHOLD_RELAXED;
	}

	private boolean isDownwardTrend(double currentMa, double prevMa) {
		if (prevMa <= 0)
			return false;
		return ((currentMa - prevMa) / prevMa) <= -MIN_SLOPE_THRESHOLD;
	}

	// 緩和版
	private boolean isDownwardTrendRelaxed(double currentMa, double prevMa) {
		if (prevMa <= 0)
			return false;
		return ((currentMa - prevMa) / prevMa) <= -MIN_SLOPE_THRESHOLD_RELAXED;
	}

	private double calculateLotSize(double price) {
		if (price <= 0)
			return 0.001;
		double size = TARGET_TRADE_AMOUNT / price;
		return Math.round(size * 10000.0) / 10000.0;
	}

	private double getPastMA(String key, int period, int barsAgo) {
		if (barsAgo == 0)
			return calculateCurrentMA(key, period);
		List<CandleData> hist = historyMap.get(key);
		if (hist == null)
			return 0.0;
		int startIndex = hist.size() - barsAgo;
		if (startIndex - period + 1 < 0)
			return 0.0;
		double sum = 0;
		for (int i = 0; i < period; i++) {
			sum += hist.get(startIndex - i).getClose();
		}
		return sum / period;
	}

	private void executeTrade(Symbol symbol, TimeFrame tf, SignalDecision decision, CandleData candle) {
		String key = symbol.name() + "_" + tf.name();
		String currentPos = positionMap.getOrDefault(key, "NONE");

		boolean isNewEntry = "NONE".equals(currentPos);
		if (isNewEntry && lastOrderTimeMap.get(key).equals(candle.getTime()))
			return;

		String newPos = currentPos;
		String actionType = "";
		double tradeSize = 0.001;

		if (isNewEntry) {
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

		log.info("★★★ [自動売買] [{}] {} 注文を実行しました。価格: {}, 数量: {} ★★★", key, actionType, candle.getClose(), tradeSize);
	}

	private double calculateCurrentMA(String key, int period) {
		List<CandleData> hist = historyMap.get(key);
		CandleData cur = currentCandleMap.get(key);
		if (hist == null || hist.size() < period - 1)
			return 0;
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
			for (int j = 0; j < period; j++)
				sum += candles.get(i - j).getClose();
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

	private boolean isSlopePositive(double currentMa, double prevMa) {
		if (prevMa <= 0)
			return false;
		return currentMa > prevMa;
	}

	private boolean isSlopeNegative(double currentMa, double prevMa) {
		if (prevMa <= 0)
			return false;
		return currentMa < prevMa;
	}

	private boolean isSlopeGreaterThanOrEqual(double currentMa, double prevMa, double threshold) {
		if (prevMa <= 0)
			return false;
		return ((currentMa - prevMa) / prevMa) >= threshold;
	}

	private boolean isSlopeLessThanOrEqual(double currentMa, double prevMa, double threshold) {
		if (prevMa <= 0)
			return false;
		return ((currentMa - prevMa) / prevMa) <= threshold;
	}

	private double getHighestHigh(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo)
			return Double.MAX_VALUE;
		double highest = 0;
		int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) {
			highest = Math.max(highest, hist.get(endIndex - i).getHigh());
		}
		return highest;
	}

	private double getLowestLow(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo)
			return 0.0;
		double lowest = Double.MAX_VALUE;
		int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) {
			lowest = Math.min(lowest, hist.get(endIndex - i).getLow());
		}
		return lowest;
	}

	private boolean isBearishSwallow(String key, CandleData current) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < 3)
			return false;
		CandleData c1 = hist.get(hist.size() - 1);
		CandleData c2 = hist.get(hist.size() - 2);
		CandleData c3 = hist.get(hist.size() - 3);
		boolean isCurrentBearish = current.getClose() < current.getOpen();
		boolean isC1Bearish = c1.getClose() < c1.getOpen();
		boolean swallow1 = isCurrentBearish && (current.getClose() < Math.min(c2.getOpen(), c2.getClose()));
		boolean swallow2 = isCurrentBearish && isC1Bearish
				&& (current.getClose() < Math.min(c3.getOpen(), c3.getClose()));
		return swallow1 || swallow2;
	}

	private double getPastCandleClose(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < barsAgo)
			return 0.0;
		return hist.get(hist.size() - barsAgo).getClose();
	}

	private double getPastCandleOpen(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < barsAgo)
			return 0.0;
		return hist.get(hist.size() - barsAgo).getOpen();
	}

	private double getPastCandleLow(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < barsAgo)
			return 0.0;
		return hist.get(hist.size() - barsAgo).getLow();
	}

	private Double getTrendLineValue(String key, boolean isResistance) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < STRATEGY8_LOOKBACK)
			return null;

		List<Integer> peakIndices = new ArrayList<>();
		List<Double> peakValues = new ArrayList<>();

		// 1. 山（抵抗線）または谷（支持線）の頂点抽出
		for (int i = hist.size() - STRATEGY8_LOOKBACK; i < hist.size() - 2; i++) {
			double p1 = isResistance ? hist.get(i - 1).getHigh() : hist.get(i - 1).getLow();
			double p2 = isResistance ? hist.get(i).getHigh() : hist.get(i).getLow();
			double p3 = isResistance ? hist.get(i + 1).getHigh() : hist.get(i + 1).getLow();

			if (isResistance) {
				if (p2 > p1 && p2 > p3) {
					peakIndices.add(i);
					peakValues.add(p2);
				}
			} else {
				if (p2 < p1 && p2 < p3) {
					peakIndices.add(i);
					peakValues.add(p2);
				}
			}
		}

		int n = peakIndices.size();
		// 頂点が3つ未満ならトレンドを形成していないと判断
		if (n < 3)
			return null;

		// 2. 最小二乗法による回帰直線の算出 ( y = slope * x + intercept )
		double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
		for (int i = 0; i < n; i++) {
			double x = peakIndices.get(i);
			double y = peakValues.get(i);
			sumX += x;
			sumY += y;
			sumXY += x * y;
			sumXX += x * x;
		}

		double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
		double intercept = (sumY - slope * sumX) / n;

		// 3. 算出した直線に対して、各頂点が綺麗に接しているか（許容誤差内か）を検証
		for (int i = 0; i < n; i++) {
			double x = peakIndices.get(i);
			double actualY = peakValues.get(i);
			double expectedY = slope * x + intercept;

			// 直線の価格と実際の頂点の価格の乖離率を計算
			double deviation = Math.abs(actualY - expectedY) / expectedY;
			if (deviation > STRATEGY8_TREND_TOLERANCE) {
				// 1つでも大きく外れる頂点があれば、信頼できるトレンド線ではないとして破棄
				return null;
			}
		}

		// 現在のインデックス（最新足）におけるトレンドラインの価格を返す
		int currentIndex = hist.size();
		return slope * currentIndex + intercept;
	}
}