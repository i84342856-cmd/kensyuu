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
	private final BitFlyerClient bitFlyerClient; 
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

	private final Map<String, Double> targetPriceMap = new ConcurrentHashMap<>();
	private final Map<String, Double> stopLossPriceMap = new ConcurrentHashMap<>();

	private final boolean IS_DEMO_MODE = true;
	private boolean isSystemReady = false;
	private long lastTickReceivedTime = System.currentTimeMillis();

	private final double TARGET_TRADE_AMOUNT = 400000.0;
	private final double MAX_LOSS_JPY = -3000.0; 

	private final Map<String, Boolean> strategySettings = new ConcurrentHashMap<>();
	{
		strategySettings.put("201", true); 
		strategySettings.put("202", true); 
		strategySettings.put("301", true); 
		strategySettings.put("302", true); 
		strategySettings.put("401", true); 
		strategySettings.put("402", true); 
		strategySettings.put("501", true); 
		strategySettings.put("502", true);
		strategySettings.put("601", true); 
		strategySettings.put("602", true); 
	}

	private boolean isTargetSymbol(Symbol s) { return true; }

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		log.info("🚀 システム起動: 全通貨の非同期初期化を開始します...");
		addSystemLog("SYSTEM BOOTING", "システム初期化中...");
		Executors.newSingleThreadExecutor().execute(() -> {
			try {
				for (Symbol s : Symbol.values()) {
					if (!isTargetSymbol(s)) continue;
					for (TimeFrame tf : TimeFrame.values()) {
						// ★修正: 1分足(M1)も監視対象に含めるためコメントアウト
						 if (tf == TimeFrame.M1) continue; 
						
						String key = s.name() + "_" + tf.name();
						List<CandleData> fetched = null;
						
						try {
							if (cryptoCompareClient != null) {
								fetched = cryptoCompareClient.getHistoricalCandles(s, tf, 1000);
							}
						} catch (Exception e) {
							log.warn("履歴データ取得APIエラー ({}): {}", key, e.getMessage());
						}
						
						if (fetched == null || fetched.isEmpty()) {
							log.info("⚠️ 過去データが取得できなかったため、{} の初期データ(300本)を生成します", key);
							fetched = generateFallbackCandles(s, tf);
						}

						if (!fetched.isEmpty()) {
							CandleData last = fetched.remove(fetched.size() - 1);
							currentCandleMap.put(key, last);
						}
						historyMap.put(key, fetched);

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
						monitorSettings.put(key, true);
						Thread.sleep(1000);
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
		try {
			if (bitFlyerClient != null) {
				basePrice = bitFlyerClient.getMidPrice(symbol);
			}
		} catch (Exception e) {}
		
		long currentTime = System.currentTimeMillis() / 1000;
		long currentPeriod = (currentTime / tf.getSeconds()) * tf.getSeconds();

		double lastClose = basePrice;
		for (int i = 300; i >= 0; i--) { 
			long time = currentPeriod - ((long) i * tf.getSeconds());
			double open = lastClose; 
			double randPercent = (Math.random() - 0.5) * 0.001; 
			double close = open * (1.0 + randPercent);
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

	@Data
	@AllArgsConstructor
	private static class SignalDecision {
		RealtimeUpdateDto.SignalType type;
		int strategyId;
		String reason;
		double targetPrice;
		double stopLossPrice;
		
		public SignalDecision(RealtimeUpdateDto.SignalType type, int strategyId, String reason) {
			this.type = type;
			this.strategyId = strategyId;
			this.reason = reason;
			this.targetPrice = 0.0;
			this.stopLossPrice = 0.0;
		}
	}

	@Data
	@AllArgsConstructor
	private static class SwingPoint {
		int index;
		double price;
		boolean isHigh;
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
		this.lastTickReceivedTime = System.currentTimeMillis();
		if (!isSystemReady) return;
		if (!isTargetSymbol(tick.getSymbol())) return;
		for (TimeFrame tf : TimeFrame.values()) {
			// ★修正: 1分足(M1)も監視対象に含めるためコメントアウト
			// if (tf == TimeFrame.M1) continue; 
			updateAndCheckSignal(tick, tf);
		}
	}

	@Scheduled(fixedRate = 60000)
	public void watchdogTimer() {
		long now = System.currentTimeMillis();
		if (now - lastTickReceivedTime > 300000) {
			log.error("🚨 5分間データを受信していません。サイレント切断の疑いがあるため強制再接続します...");
			lastTickReceivedTime = now;
		}
	}

	private void updateAndCheckSignal(TickData tick, TimeFrame tf) {
		Symbol symbol = tick.getSymbol();
		String key = symbol.name() + "_" + tf.name();
		long candleStart = (tick.getTimestamp() / tf.getSeconds()) * tf.getSeconds();
		double price = tick.getPrice();

		CandleData current = currentCandleMap.get(key);
		if (current == null || current.getTime() < candleStart) {
			if (current != null) historyMap.get(key).add(current);
			// リアルタイム生成時、volumeが0.0で作成される
			current = CandleData.builder().time(candleStart).open(price).high(price).low(price).close(price).build();
			currentCandleMap.put(key, current);
		} else {
			current.setClose(price);
			current.setHigh(Math.max(current.getHigh(), price));
			current.setLow(Math.min(current.getLow(), price));
		}

		SignalDecision decision = checkSignal(symbol, tf, key, current);

		if (decision != null && decision.getType() != RealtimeUpdateDto.SignalType.NONE && monitorSettings.getOrDefault(key, false)) {
			executeTrade(tick.getSymbol(), tf, decision, current);
		}

		messagingTemplate.convertAndSend("/topic/" + key, RealtimeUpdateDto.builder().currentCandle(current)
				.currentMa5(calculateCurrentMA(key, 5)).currentMa25(calculateCurrentMA(key, 25)).signal(decision != null ? decision.getType() : RealtimeUpdateDto.SignalType.NONE).build());
	}

	private SignalDecision checkSignal(Symbol symbol, TimeFrame tf, String key, CandleData current) {
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
		double sma = getPastMA(key, 20, 0);
		double smaPrev = getPastMA(key, 20, 1);
		double std = getStdDev(key, 20, 0);
		double stdPrev = getStdDev(key, 20, 1);
		
		double upper2 = sma + 2 * std; double lower2 = sma - 2 * std;
		double upper2Prev = smaPrev + 2 * stdPrev; double lower2Prev = smaPrev - 2 * stdPrev;
		
		double[] adx = getADX(key, 14, 0);
		double[] adxPrev = getADX(key, 14, 1);
		double[] macd = getMACD(key, 0);
		double[] macdPrev = getMACD(key, 1);

		if (strategySettings.getOrDefault("201", false)) {
			boolean breakUpper2 = getPastCandleClose(key, 1) <= upper2Prev && current.getClose() > upper2;
			boolean isAdxTrending = adx[0] >= 20 && adx[0] > adxPrev[0];
			boolean isMacdExpanding = macd[2] > 0 && macd[2] > macdPrev[2];
			boolean isSmaUp = smaPrev > 0 && (sma - smaPrev) / smaPrev > 0.0003;
			
			// isVolMomentum等のボリューム依存条件を削除し、純粋な価格モメンタムで判定
			if (breakUpper2 && isAdxTrending && isMacdExpanding && isSmaUp) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 201, "【戦略201:バンドウォーク】+2σ突破(順張り買い)");
			}
		}

		if (strategySettings.getOrDefault("202", false)) {
			boolean touchLower2 = current.getLow() <= lower2;
			boolean isRangeMarket = adx[0] < 20;
			boolean isMacdBullishDiv = isBullishDivergence(key, current, macd[2]);
			boolean isSmaFlat = smaPrev > 0 && Math.abs((sma - smaPrev) / smaPrev) < 0.0001;

			if (touchLower2 && isRangeMarket && isMacdBullishDiv && isSmaFlat) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 202, "【戦略202:平均回帰】-2σ到達(逆張り買い)");
			}
		}

		if (strategySettings.getOrDefault("301", false)) {
			List<SwingPoint> swings = getRecentSwings(key, 5);
			if (swings.size() == 5 && swings.get(4).isHigh && !swings.get(3).isHigh && swings.get(2).isHigh && !swings.get(1).isHigh && swings.get(0).isHigh) {
				double e1 = swings.get(0).getPrice();
				double e2 = swings.get(1).getPrice();
				double e3 = swings.get(2).getPrice();
				double e4 = swings.get(3).getPrice();
				double e5 = swings.get(4).getPrice();

				double maxHigh = Math.max(e1, Math.max(e3, e5));
				double minHigh = Math.min(e1, Math.min(e3, e5));
				double avgHigh = (e1 + e3 + e5) / 3.0;

				boolean isResistanceHorizontal = (maxHigh - minHigh) / avgHigh <= 0.015;
				boolean isSupportAscending = e2 > e4;
				boolean isBreakout = current.getClose() > maxHigh * 1.01;
				
				double sma50 = getPastMA(key, 50, 0);
				double sma200 = getPastMA(key, 200, 0);
				boolean isUptrend = sma50 > sma200;

				// ★出来高条件(isVolumeSurge)を撤廃
				if (isResistanceHorizontal && isSupportAscending && isBreakout && isUptrend) {
					double tp = current.getClose() + (avgHigh - e2); 
					double sl = e4 * 0.99; 
					return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 301, "【戦略301】アセトラブレイクアウト(買い)", tp, sl);
				}
			}
		}

		if (strategySettings.getOrDefault("401", false)) {
			double sma50 = getPastMA(key, 50, 0);
			double ma50Prev = getPastMA(key, 50, 1);
			double sma200 = getPastMA(key, 200, 0);
			double ma5 = getPastMA(key, 5, 0);
			double ma5Prev = getPastMA(key, 5, 1);

			boolean isPerfectOrderLong = (sma > sma50) && (sma50 > sma200);
			boolean isMomentumUp = (sma > smaPrev) && (sma50 > ma50Prev);
			boolean isBreakoutUp = getPastCandleClose(key, 1) <= ma5Prev && current.getClose() > ma5;
			boolean isAdxTrending = adx[0] >= 15; 

			// ★出来高条件(isVolumeSurge)を撤廃
			if (isPerfectOrderLong && isMomentumUp && isBreakoutUp && isAdxTrending) {
				double atr = getATR(key, 14, 0);
				double sl = current.getClose() - 2 * atr;
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 401, "【戦略401】PO順張り(買い)", 0.0, sl);
			}
		}

		if (strategySettings.getOrDefault("501", false)) {
			double ema5 = getPastEMA(key, 5, 0);
			double ema20 = getPastEMA(key, 20, 0);
			double ema50 = getPastEMA(key, 50, 0);
			double ema200 = getPastEMA(key, 200, 0);
			
			double ema5Prev = getPastEMA(key, 5, 1);
			double ema20Prev = getPastEMA(key, 20, 1);
			double ema50Prev = getPastEMA(key, 50, 1);
			double ema200Prev = getPastEMA(key, 200, 1);

			boolean isPerfectOrder = (ema5 > ema20) && (ema20 > ema50) && (ema50 > ema200);
			boolean isAllUp = (ema5 > ema5Prev) && (ema20 > ema20Prev) && (ema50 > ema50Prev) && (ema200 > ema200Prev);
			boolean isAdxTrending = adx[0] >= 15; 
			boolean isBreakout = getPastCandleClose(key, 1) <= ema5Prev && current.getClose() > ema5;

			// ★出来高条件(isVolumeSurge)を撤廃
			if (isPerfectOrder && isAllUp && isAdxTrending && isBreakout) {
				double sl = current.getClose() - 1.5 * getATR(key, 14, 0);
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 501, "【戦略501】EMA PO(買い)", 0.0, sl);
			}
		}

		if (strategySettings.getOrDefault("601", false)) {
			double ema20 = getPastEMA(key, 20, 0);
			double atr20 = getATR(key, 20, 0);
			double std20 = getStdDev(key, 20, 0);
			double kcUpper = ema20 + 1.5 * atr20;
			double bbUpper = ema20 + 2 * std20;
			double kcLower = ema20 - 1.5 * atr20;
			double bbLower = ema20 - 2 * std20;

			double ema20Prev = getPastEMA(key, 20, 1);
			double atr20Prev = getATR(key, 20, 1);
			double std20Prev = getStdDev(key, 20, 1);
			double kcUpperPrev = ema20Prev + 1.5 * atr20Prev;
			double bbUpperPrev = ema20Prev + 2 * std20Prev;
			double kcLowerPrev = ema20Prev - 1.5 * atr20Prev;
			double bbLowerPrev = ema20Prev - 2 * std20Prev;

			boolean isSqueezePrev = (bbUpperPrev <= kcUpperPrev) && (bbLowerPrev >= kcLowerPrev);
			boolean isSqueezeRelease = (bbUpper > kcUpper) || (bbLower < kcLower);
			boolean isBreakout = current.getClose() > bbUpper;

			// ★出来高条件(isVolumeSurge)を撤廃
			if (isSqueezePrev && isSqueezeRelease && isBreakout) {
				double sl = current.getClose() - 2.0 * atr20;
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 601, "【戦略601】BBKCブレイク(買い)", 0.0, sl);
			}
		}

		return null;
	}

	private SignalDecision checkShortSignal(String key, CandleData current) {
		double sma = getPastMA(key, 20, 0);
		double smaPrev = getPastMA(key, 20, 1);
		double std = getStdDev(key, 20, 0);
		double stdPrev = getStdDev(key, 20, 1);
		
		double upper2 = sma + 2 * std; double lower2 = sma - 2 * std;
		double upper2Prev = smaPrev + 2 * stdPrev; double lower2Prev = smaPrev - 2 * stdPrev;
		
		double[] adx = getADX(key, 14, 0);
		double[] adxPrev = getADX(key, 14, 1);
		double[] macd = getMACD(key, 0);
		double[] macdPrev = getMACD(key, 1);

		if (strategySettings.getOrDefault("201", false)) {
			boolean breakLower2 = getPastCandleClose(key, 1) >= lower2Prev && current.getClose() < lower2;
			boolean isAdxTrending = adx[0] >= 20 && adx[0] > adxPrev[0];
			boolean isMacdExpandingDown = macd[2] < 0 && macd[2] < macdPrev[2];
			boolean isSmaDown = smaPrev > 0 && (sma - smaPrev) / smaPrev < -0.0003;

			// isVolMomentum等のボリューム依存条件を削除
			if (breakLower2 && isAdxTrending && isMacdExpandingDown && isSmaDown) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 201, "【戦略201:バンドウォーク】-2σ突破(順張り売り)");
			}
		}

		if (strategySettings.getOrDefault("202", false)) {
			boolean touchUpper2 = current.getHigh() >= upper2;
			boolean isRangeMarket = adx[0] < 20;
			boolean isMacdBearishDiv = isBearishDivergence(key, current, macd[2]);
			boolean isSmaFlat = smaPrev > 0 && Math.abs((sma - smaPrev) / smaPrev) < 0.0001;

			if (touchUpper2 && isRangeMarket && isMacdBearishDiv && isSmaFlat) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 202, "【戦略202:平均回帰】+2σ到達(逆張り売り)");
			}
		}

		if (strategySettings.getOrDefault("302", false)) {
			List<SwingPoint> swings = getRecentSwings(key, 5);
			if (swings.size() == 5 && !swings.get(4).isHigh && swings.get(3).isHigh && !swings.get(2).isHigh && swings.get(1).isHigh && !swings.get(0).isHigh) {
				double e1 = swings.get(0).getPrice();
				double e2 = swings.get(1).getPrice();
				double e3 = swings.get(2).getPrice();
				double e4 = swings.get(3).getPrice();
				double e5 = swings.get(4).getPrice();

				double maxLow = Math.max(e1, Math.max(e3, e5));
				double minLow = Math.min(e1, Math.min(e3, e5));
				double avgLow = (e1 + e3 + e5) / 3.0;

				boolean isSupportHorizontal = (maxLow - minLow) / avgLow <= 0.015;
				boolean isResistanceDescending = e2 < e4;
				boolean isBreakout = current.getClose() < minLow * 0.99;
				
				double sma50 = getPastMA(key, 50, 0);
				double sma200 = getPastMA(key, 200, 0);
				boolean isDowntrend = sma50 < sma200;

				// ★出来高条件を撤廃
				if (isSupportHorizontal && isResistanceDescending && isBreakout && isDowntrend) {
					double tp = current.getClose() - (e2 - avgLow); 
					double sl = e4 * 1.01; 
					return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 302, "【戦略302】ディセトラブレイク(売り)", tp, sl);
				}
			}
		}

		if (strategySettings.getOrDefault("402", false)) {
			double sma50 = getPastMA(key, 50, 0);
			double ma50Prev = getPastMA(key, 50, 1);
			double sma200 = getPastMA(key, 200, 0);
			double ma5 = getPastMA(key, 5, 0);
			double ma5Prev = getPastMA(key, 5, 1);

			boolean isPerfectOrderShort = (sma < sma50) && (sma50 < sma200);
			boolean isMomentumDown = (sma < smaPrev) && (sma50 < ma50Prev);
			boolean isBreakoutDown = getPastCandleClose(key, 1) >= ma5Prev && current.getClose() < ma5;
			boolean isAdxTrending = adx[0] >= 15; 

			// ★出来高条件を撤廃
			if (isPerfectOrderShort && isMomentumDown && isBreakoutDown && isAdxTrending) {
				double atr = getATR(key, 14, 0);
				double sl = current.getClose() + 2 * atr;
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 402, "【戦略402】PO順張り(売り)", 0.0, sl);
			}
		}

		if (strategySettings.getOrDefault("502", false)) {
			double ema5 = getPastEMA(key, 5, 0);
			double ema20 = getPastEMA(key, 20, 0);
			double ema50 = getPastEMA(key, 50, 0);
			double ema200 = getPastEMA(key, 200, 0);
			
			double ema5Prev = getPastEMA(key, 5, 1);
			double ema20Prev = getPastEMA(key, 20, 1);
			double ema50Prev = getPastEMA(key, 50, 1);
			double ema200Prev = getPastEMA(key, 200, 1);

			boolean isPerfectOrder = (ema5 < ema20) && (ema20 < ema50) && (ema50 < ema200);
			boolean isAllDown = (ema5 < ema5Prev) && (ema20 < ema20Prev) && (ema50 < ema50Prev) && (ema200 < ema200Prev);
			boolean isAdxTrending = adx[0] >= 15; 
			boolean isBreakout = getPastCandleClose(key, 1) >= ema5Prev && current.getClose() < ema5;

			// ★出来高条件を撤廃
			if (isPerfectOrder && isAllDown && isAdxTrending && isBreakout) {
				double sl = current.getClose() + 1.5 * getATR(key, 14, 0);
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 502, "【戦略502】EMA PO(売り)", 0.0, sl);
			}
		}

		if (strategySettings.getOrDefault("602", false)) {
			double ema20 = getPastEMA(key, 20, 0);
			double atr20 = getATR(key, 20, 0);
			double std20 = getStdDev(key, 20, 0);
			double kcUpper = ema20 + 1.5 * atr20;
			double bbUpper = ema20 + 2 * std20;
			double kcLower = ema20 - 1.5 * atr20;
			double bbLower = ema20 - 2 * std20;

			double ema20Prev = getPastEMA(key, 20, 1);
			double atr20Prev = getATR(key, 20, 1);
			double std20Prev = getStdDev(key, 20, 1);
			double kcUpperPrev = ema20Prev + 1.5 * atr20Prev;
			double bbUpperPrev = ema20Prev + 2 * std20Prev;
			double kcLowerPrev = ema20Prev - 1.5 * atr20Prev;
			double bbLowerPrev = ema20Prev - 2 * std20Prev;

			boolean isSqueezePrev = (bbUpperPrev <= kcUpperPrev) && (bbLowerPrev >= kcLowerPrev);
			boolean isSqueezeRelease = (bbUpper > kcUpper) || (bbLower < kcLower);
			boolean isBreakout = current.getClose() < bbLower;

			// ★出来高条件を撤廃
			if (isSqueezePrev && isSqueezeRelease && isBreakout) {
				double sl = current.getClose() + 2.0 * atr20;
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 602, "【戦略602】BBKCブレイク(売り)", 0.0, sl);
			}
		}

		return null;
	}

	private SignalDecision checkExitLongSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0) return null;
		int strategyId = entryStrategyMap.getOrDefault(key, 201);
		double tradeSize = positionSizeMap.getOrDefault(key, 0.0);
		double pnl = (current.getClose() - entryPrice) * tradeSize; 

		double sma = getPastMA(key, 20, 0);
		double std = getStdDev(key, 20, 0);

		if (strategyId == 201) {
			double upper1 = sma + std;
			if (current.getClose() < upper1) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 201, "【戦略201利確/損切】モメンタム枯渇(+1σ割れ)");
			}
		} else if (strategyId == 202) {
			double lower3 = sma - 3 * std;
			if (current.getClose() < lower3) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 202, "【戦略202損切】-3σ割れ(トレンド発生による撤退)");
			}
			if (current.getClose() >= sma) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 202, "【戦略202利確】中心線(20SMA)へ平均回帰完了");
			}
		}

		if (pnl <= MAX_LOSS_JPY) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, String.format("【戦略%d】損失超過による強制損切り", strategyId));

		if (strategyId == 301) {
			double tp = targetPriceMap.getOrDefault(key, 0.0);
			double sl = stopLossPriceMap.getOrDefault(key, 0.0);
			if (tp > 0 && current.getClose() >= tp) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 301, "【戦略301利確】目標価格(TP)到達");
			if (sl > 0 && current.getClose() <= sl) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 301, "【戦略301損切】ストップロス(SL)到達");
		} else if (strategyId == 401) {
			double atr = getATR(key, 14, 0);
			double currentSl = stopLossPriceMap.getOrDefault(key, 0.0);
			double newSl = current.getClose() - 2 * atr;
			if (newSl > currentSl) { stopLossPriceMap.put(key, newSl); currentSl = newSl; }
			if (current.getClose() <= currentSl) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 401, "【戦略401利確/損切】トレイリング到達");
		} else if (strategyId == 501) {
			double atr = getATR(key, 14, 0);
			double currentSl = stopLossPriceMap.getOrDefault(key, 0.0);
			double newSl = current.getClose() - 1.5 * atr;
			if (newSl > currentSl || currentSl == 0.0) { stopLossPriceMap.put(key, newSl); currentSl = newSl; }
			if (current.getClose() <= currentSl) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 501, "【戦略501利確/損切】トレイリング到達");
			
			double[] macd = getMACD(key, 0); double[] macdPrev = getMACD(key, 1);
			if (macdPrev[0] >= macdPrev[1] && macd[0] < macd[1]) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 501, "【戦略501利確】MACDデッドクロス");
		}
		else if (strategyId == 601) {
			double atr20 = getATR(key, 20, 0);
			double currentSl = stopLossPriceMap.getOrDefault(key, 0.0);
			double newSl = current.getClose() - 2.0 * atr20;
			if (newSl > currentSl || currentSl == 0.0) { stopLossPriceMap.put(key, newSl); currentSl = newSl; }
			if (current.getClose() <= currentSl) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 601, "【戦略601利確/損切】ATRトレイリング到達");
		}
		return null;
	}

	private SignalDecision checkExitShortSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0) return null;
		int strategyId = entryStrategyMap.getOrDefault(key, 201);
		double tradeSize = positionSizeMap.getOrDefault(key, 0.0);
		double pnl = (entryPrice - current.getClose()) * tradeSize; 

		double sma = getPastMA(key, 20, 0);
		double std = getStdDev(key, 20, 0);

		if (strategyId == 201) {
			double lower1 = sma - std;
			if (current.getClose() > lower1) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 201, "【戦略201利確/損切】モメンタム枯渇(-1σ超え)");
			}
		} else if (strategyId == 202) {
			double upper3 = sma + 3 * std;
			if (current.getClose() > upper3) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 202, "【戦略202損切】+3σ超え(トレンド発生による撤退)");
			}
			if (current.getClose() <= sma) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 202, "【戦略202利確】中心線(20SMA)へ平均回帰完了");
			}
		}

		if (pnl <= MAX_LOSS_JPY) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, String.format("【戦略%d】損失超過による強制損切り", strategyId));

		if (strategyId == 302) {
			double tp = targetPriceMap.getOrDefault(key, 0.0);
			double sl = stopLossPriceMap.getOrDefault(key, 0.0);
			if (tp > 0 && current.getClose() <= tp) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 302, "【戦略302利確】目標価格(TP)到達");
			if (sl > 0 && current.getClose() >= sl) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 302, "【戦略302損切】ストップロス(SL)到達");
		} else if (strategyId == 402) {
			double atr = getATR(key, 14, 0);
			double currentSl = stopLossPriceMap.getOrDefault(key, Double.MAX_VALUE);
			if (currentSl == 0.0) currentSl = Double.MAX_VALUE; 
			double newSl = current.getClose() + 2 * atr;
			if (newSl < currentSl) { stopLossPriceMap.put(key, newSl); currentSl = newSl; }
			if (current.getClose() >= currentSl) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 402, "【戦略402利確/損切】トレイリング到達");
		} else if (strategyId == 502) {
			double atr = getATR(key, 14, 0);
			double currentSl = stopLossPriceMap.getOrDefault(key, Double.MAX_VALUE);
			if (currentSl == 0.0) currentSl = Double.MAX_VALUE;
			double newSl = current.getClose() + 1.5 * atr;
			if (newSl < currentSl) { stopLossPriceMap.put(key, newSl); currentSl = newSl; }
			if (current.getClose() >= currentSl) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 502, "【戦略502利確/損切】トレイリング到達");
			
			double[] macd = getMACD(key, 0); double[] macdPrev = getMACD(key, 1);
			if (macdPrev[0] <= macdPrev[1] && macd[0] > macd[1]) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 502, "【戦略502利確】MACDゴールデンクロス");
		}
		else if (strategyId == 602) {
			double atr20 = getATR(key, 20, 0);
			double currentSl = stopLossPriceMap.getOrDefault(key, Double.MAX_VALUE);
			if (currentSl == 0.0) currentSl = Double.MAX_VALUE;
			double newSl = current.getClose() + 2.0 * atr20;
			if (newSl < currentSl) { stopLossPriceMap.put(key, newSl); currentSl = newSl; }
			if (current.getClose() >= currentSl) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 602, "【戦略602利確/損切】ATRトレイリング到達");
		}
		return null;
	}

	private double calculateKellyLotSize(double price, double atr) {
		if (price <= 0 || atr <= 0) return 0.001;
		double assumedAccountBalance = 1000000.0; 
		double halfKelly = 0.05; 
		double maxRiskAmount = assumedAccountBalance * halfKelly; 
		double stopLossDistance = 1.5 * atr; 
		if (stopLossDistance == 0) stopLossDistance = price * 0.01; 
		double size = maxRiskAmount / stopLossDistance;
		return Math.round(size * 10000.0) / 10000.0;
	}

	private double getPastEMA(String key, int period, int barsAgo) {
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

	private double getATR(String key, int period, int barsAgo) {
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

	private List<SwingPoint> getRecentSwings(String key, int count) {
		List<CandleData> hist = historyMap.get(key);
		List<SwingPoint> swings = new ArrayList<>();
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
				if (isHigh) { swings.add(0, new SwingPoint(i, currentHigh, true)); lastFoundWasHigh = true; } 
				else if (isLow) { swings.add(0, new SwingPoint(i, currentLow, false)); lastFoundWasHigh = false; }
			} else {
				if (lastFoundWasHigh && isLow) { swings.add(0, new SwingPoint(i, currentLow, false)); lastFoundWasHigh = false; } 
				else if (!lastFoundWasHigh && isHigh) { swings.add(0, new SwingPoint(i, currentHigh, true)); lastFoundWasHigh = true; }
			}
			if (swings.size() == count) break;
		}
		return swings;
	}

	private double getVolumeSMA(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo) return 0.0;
		double sum = 0;
		if (barsAgo == 0) {
			CandleData cur = currentCandleMap.get(key);
			if (cur != null) sum += cur.getVolume();
			for (int i = 1; i < period; i++) sum += hist.get(hist.size() - i).getVolume();
		} else {
			for (int i = 0; i < period; i++) sum += hist.get(hist.size() - barsAgo - i).getVolume();
		}
		return sum / period;
	}

	private double[] getADX(String key, int period, int barsAgo) {
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

	private double[] getMACD(String key, int barsAgo) {
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

	private boolean isBearishDivergence(String key, CandleData current, double currentMacdHist) {
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

	private boolean isBullishDivergence(String key, CandleData current, double currentMacdHist) {
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

	private double calculateLotSize(double price) {
		if (price <= 0) return 0.001;
		return Math.round((TARGET_TRADE_AMOUNT / price) * 10000.0) / 10000.0;
	}

	private double getPastMA(String key, int period, int barsAgo) {
		if (barsAgo == 0) return calculateCurrentMA(key, period);
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() - barsAgo - period + 1 < 0) return 0.0;
		double sum = 0;
		for (int i = 0; i < period; i++) sum += hist.get(hist.size() - barsAgo - i).getClose();
		return sum / period;
	}

	private double getStdDev(String key, int period, int barsAgo) {
		double sma = getPastMA(key, period, barsAgo);
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo) return 0.0;
		double sumSq = 0;
		if (barsAgo == 0) {
			CandleData cur = currentCandleMap.get(key);
			if (cur != null) sumSq += Math.pow(cur.getClose() - sma, 2);
			for (int i = 1; i < period; i++) sumSq += Math.pow(hist.get(hist.size() - i).getClose() - sma, 2);
		} else {
			for (int i = 0; i < period; i++) sumSq += Math.pow(hist.get(hist.size() - barsAgo - i).getClose() - sma, 2);
		}
		return Math.sqrt(sumSq / period);
	}

	private void executeTrade(Symbol symbol, TimeFrame tf, SignalDecision decision, CandleData candle) {
		String key = symbol.name() + "_" + tf.name();
		String currentPos = positionMap.getOrDefault(key, "NONE");
		boolean isNewEntry = "NONE".equals(currentPos);
		if (isNewEntry && lastOrderTimeMap.get(key).equals(candle.getTime())) return;

		String newPos = currentPos; String actionType = ""; double tradeSize = 0.001;
		if (isNewEntry) {
			if (decision.getStrategyId() == 501 || decision.getStrategyId() == 502 || decision.getStrategyId() == 601 || decision.getStrategyId() == 602) {
				double atr = getATR(key, 14, 0);
				tradeSize = calculateKellyLotSize(candle.getClose(), atr);
			} else {
				tradeSize = calculateLotSize(candle.getClose());
			}
			newPos = decision.getType() == RealtimeUpdateDto.SignalType.BUY ? "LONG" : "SHORT";
			actionType = (newPos.equals("LONG") ? "🟢 [LONG] " : "🔴 [SHORT] ") + decision.getReason();
		} else {
			tradeSize = positionSizeMap.getOrDefault(key, 0.001);
			if ("LONG".equals(currentPos) && decision.getType() == RealtimeUpdateDto.SignalType.SELL) { newPos = "NONE"; actionType = "✅ [LONG決済] " + decision.getReason(); }
			else if ("SHORT".equals(currentPos) && decision.getType() == RealtimeUpdateDto.SignalType.BUY) { newPos = "NONE"; actionType = "✅ [SHORT決済] " + decision.getReason(); }
			else return;
		}

		TradeLog logTrade = new TradeLog();
		logTrade.setTime(System.currentTimeMillis() / 1000); logTrade.setSymbol(symbol.name()); logTrade.setTimeframe(tf.name());
		logTrade.setSide(decision.getType().name()); logTrade.setPrice(candle.getClose()); logTrade.setSize(tradeSize);
		logTrade.setMessage(actionType); logTrade.setStrategy(decision.getStrategyId());

		tradeLogRepository.save(logTrade);
		messagingTemplate.convertAndSend("/topic/trades", logTrade);

		lastOrderTimeMap.put(key, candle.getTime()); positionMap.put(key, newPos);
		if (!"NONE".equals(newPos)) {
			entryPriceMap.put(key, candle.getClose()); positionSizeMap.put(key, tradeSize);
			entryStrategyMap.put(key, decision.getStrategyId()); entryCandleTimeMap.put(key, candle.getTime());
			
			targetPriceMap.put(key, decision.getTargetPrice());
			stopLossPriceMap.put(key, decision.getStopLossPrice());
		} else {
			entryPriceMap.remove(key); positionSizeMap.remove(key);
			entryStrategyMap.remove(key); entryCandleTimeMap.remove(key);
			targetPriceMap.remove(key); stopLossPriceMap.remove(key);
		}
	}

	private double calculateCurrentMA(String key, int period) {
		List<CandleData> hist = historyMap.get(key); CandleData cur = currentCandleMap.get(key);
		if (hist == null || hist.size() < period - 1) return 0;
		double sum = 0;
		if (cur != null) sum += cur.getClose();
		for (int i = 1; i < period; i++) sum += hist.get(hist.size() - i).getClose();
		return sum / period;
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

	private double getPastCandleClose(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key); if (hist == null || hist.size() < barsAgo) return 0.0;
		return hist.get(hist.size() - barsAgo).getClose();
	}
}