package com.example.cryptotool.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import jakarta.annotation.PostConstruct;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
	private long lastTickReceivedTime = System.currentTimeMillis();

	// 取引額設定
	private final double TARGET_TRADE_AMOUNT = 400000.0;
	private final double MAX_LOSS_JPY = -3000.0; 

	// --- ボリンジャーバンド完全準拠・新戦略のON/OFF ---
	private final Map<String, Boolean> strategySettings = new ConcurrentHashMap<>();
	{
		strategySettings.put("201", true); // 戦略201: バンドウォーク（順張り）
		strategySettings.put("202", true); // 戦略202: 平均回帰（逆張り）
	}

	private boolean isTargetSymbol(Symbol s) {
		return true; 
	}

	@PostConstruct
	public void init() {
		log.info("🚀 システム起動: 全通貨の非同期初期化を開始します...");
		addSystemLog("SYSTEM BOOTING", "システム初期化中...");
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
						monitorSettings.put(key, hasPosition);
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

	public void updateMonitorSetting(String symbol, String timeframe, boolean active) {
		monitorSettings.put(symbol + "_" + timeframe, active);
	}
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
			if (tf == TimeFrame.M1) continue; 
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
		
		double percentB = (current.getClose() - lower2) / (upper2 - lower2);
		double bandWidth = (upper2 - lower2) / sma;
		
		double[] adx = getADX(key, 14, 0);
		double[] adxPrev = getADX(key, 14, 1);
		double mfi = getMFI(key, 14, 0);
		double[] macd = getMACD(key, 0);
		double[] macdPrev = getMACD(key, 1);
		double[] ichi = getIchimoku(key, 0);

		// --- 戦略201: バンドウォーク（順張り LONG） ---
		if (strategySettings.getOrDefault("201", false)) {
			boolean breakUpper2 = getPastCandleClose(key, 1) <= upper2Prev && current.getClose() > upper2;
			boolean isAdxTrending = adx[0] >= 25 && adx[0] > adxPrev[0];
			boolean isVolMomentum = percentB >= 0.8 && mfi >= 80;
			boolean isMacdExpanding = macd[2] > 0 && macd[2] > macdPrev[2];
			boolean isSmaUp = (sma - smaPrev) / smaPrev > 0.0003;
			boolean isSqueezeToExpansion = checkSqueeze(key, 50, bandWidth, 0);
			boolean isLowerBandExpandingDown = lower2 < lower2Prev;
			boolean isBullishCloud = current.getClose() > Math.max(ichi[2], ichi[3]) && ichi[0] > ichi[1];

			if (breakUpper2 && isAdxTrending && isVolMomentum && isMacdExpanding && isSmaUp && isSqueezeToExpansion && isLowerBandExpandingDown && isBullishCloud) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 201, "【戦略201:バンドウォーク】+2σ突破+モメンタム加速(順張り買い)");
			}
		}

		// --- 戦略202: 平均回帰（逆張り LONG） ---
		if (strategySettings.getOrDefault("202", false)) {
			boolean touchLower2 = current.getLow() <= lower2;
			boolean isRangeMarket = adx[0] < 25 && adx[0] <= adxPrev[0];
			boolean isMacdBullishDiv = isBullishDivergence(key, current, macd[2]);
			boolean isSmaFlat = Math.abs((sma - smaPrev) / smaPrev) < 0.0001;
			boolean isWBottom = isWBottomFormation(key, current, lower2);

			if ((touchLower2 || isWBottom) && isRangeMarket && isMacdBullishDiv && isSmaFlat) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 202, "【戦略202:平均回帰】-2σ到達+レンジ内ダイバージェンス(逆張り買い)");
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
		
		double percentB = (current.getClose() - lower2) / (upper2 - lower2);
		double bandWidth = (upper2 - lower2) / sma;
		
		double[] adx = getADX(key, 14, 0);
		double[] adxPrev = getADX(key, 14, 1);
		double mfi = getMFI(key, 14, 0);
		double[] macd = getMACD(key, 0);
		double[] macdPrev = getMACD(key, 1);
		double[] ichi = getIchimoku(key, 0);

		// --- 戦略201: バンドウォーク（順張り SHORT） ---
		if (strategySettings.getOrDefault("201", false)) {
			boolean breakLower2 = getPastCandleClose(key, 1) >= lower2Prev && current.getClose() < lower2;
			boolean isAdxTrending = adx[0] >= 25 && adx[0] > adxPrev[0];
			boolean isVolMomentum = percentB <= 0.2 && mfi <= 20;
			boolean isMacdExpandingDown = macd[2] < 0 && macd[2] < macdPrev[2];
			boolean isSmaDown = (sma - smaPrev) / smaPrev < -0.0003;
			boolean isSqueezeToExpansion = checkSqueeze(key, 50, bandWidth, 0);
			boolean isUpperBandExpandingUp = upper2 > upper2Prev;
			boolean isBearishCloud = current.getClose() < Math.min(ichi[2], ichi[3]) && ichi[0] < ichi[1];

			if (breakLower2 && isAdxTrending && isVolMomentum && isMacdExpandingDown && isSmaDown && isSqueezeToExpansion && isUpperBandExpandingUp && isBearishCloud) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 201, "【戦略201:バンドウォーク】-2σ突破+モメンタム加速(順張り売り)");
			}
		}

		// --- 戦略202: 平均回帰（逆張り SHORT） ---
		if (strategySettings.getOrDefault("202", false)) {
			boolean touchUpper2 = current.getHigh() >= upper2;
			boolean isRangeMarket = adx[0] < 25 && adx[0] <= adxPrev[0];
			boolean isMacdBearishDiv = isBearishDivergence(key, current, macd[2]);
			boolean isSmaFlat = Math.abs((sma - smaPrev) / smaPrev) < 0.0001;
			boolean isMTop = isMTopFormation(key, current, upper2);

			if ((touchUpper2 || isMTop) && isRangeMarket && isMacdBearishDiv && isSmaFlat) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 202, "【戦略202:平均回帰】+2σ到達+レンジ内ダイバージェンス(逆張り売り)");
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

		if (pnl <= MAX_LOSS_JPY) return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, strategyId, String.format("【戦略%d】損失超過による強制損切り", strategyId));

		double sma = getPastMA(key, 20, 0);
		double std = getStdDev(key, 20, 0);

		if (strategyId == 201) { // バンドウォーク LONG のエグジット
			double lower2 = sma - 2 * std;
			double lower2Prev = getPastMA(key, 20, 1) - 2 * getStdDev(key, 20, 1);
			double upper1 = sma + std;
			// 逆側バンドが反転したか、または+1σゾーンを割った時
			if (lower2 > lower2Prev || current.getClose() < upper1) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 201, "【戦略201利確】モメンタム枯渇(+1σ割れ or バンド反転)");
			}
		} else if (strategyId == 202) { // 平均回帰 LONG のエグジット
			if (current.getClose() >= sma) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.SELL, 202, "【戦略202利確】中心線(20SMA)へ平均回帰完了");
			}
		}
		return null;
	}

	private SignalDecision checkExitShortSignal(TimeFrame tf, String key, CandleData current) {
		double entryPrice = entryPriceMap.getOrDefault(key, 0.0);
		if (entryPrice <= 0) return null;
		int strategyId = entryStrategyMap.getOrDefault(key, 201);
		double tradeSize = positionSizeMap.getOrDefault(key, 0.0);
		double pnl = (entryPrice - current.getClose()) * tradeSize; 

		if (pnl <= MAX_LOSS_JPY) return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, strategyId, String.format("【戦略%d】損失超過による強制損切り", strategyId));

		double sma = getPastMA(key, 20, 0);
		double std = getStdDev(key, 20, 0);

		if (strategyId == 201) { // バンドウォーク SHORT のエグジット
			double upper2 = sma + 2 * std;
			double upper2Prev = getPastMA(key, 20, 1) + 2 * getStdDev(key, 20, 1);
			double lower1 = sma - std;
			// 逆側バンドが反転したか、または-1σゾーンを割った時
			if (upper2 < upper2Prev || current.getClose() > lower1) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 201, "【戦略201利確】モメンタム枯渇(-1σ超え or バンド反転)");
			}
		} else if (strategyId == 202) { // 平均回帰 SHORT のエグジット
			if (current.getClose() <= sma) {
				return new SignalDecision(RealtimeUpdateDto.SignalType.BUY, 202, "【戦略202利確】中心線(20SMA)へ平均回帰完了");
			}
		}
		return null;
	}

	// ==========================================
	// テクニカル指標・構造計算メソッド（新規追加）
	// ==========================================

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
		double pdi = 100 * (smoothedPDM / smoothedTR);
		double ndi = 100 * (smoothedNDM / smoothedTR);
		double dx = 100 * Math.abs(pdi - ndi) / (pdi + ndi == 0 ? 1 : pdi + ndi);
		return new double[]{dx, pdi, ndi}; 
	}

	private double getMFI(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < period + barsAgo + 1) return 50.0;
		int endIndex = hist.size() - 1 - barsAgo;
		double pmf = 0, nmf = 0;
		for (int i = endIndex - period + 1; i <= endIndex; i++) {
			CandleData curr = hist.get(i); CandleData prev = hist.get(i-1);
			double typCurr = (curr.getHigh() + curr.getLow() + curr.getClose()) / 3;
			double typPrev = (prev.getHigh() + prev.getLow() + prev.getClose()) / 3;
			double flow = typCurr * (curr.getVolume() > 0 ? curr.getVolume() : 1.0); 
			if (typCurr > typPrev) pmf += flow; else if (typCurr < typPrev) nmf += flow;
		}
		if (nmf == 0) return 100.0;
		return 100.0 - (100.0 / (1 + (pmf / nmf)));
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

	private double[] getIchimoku(String key, int barsAgo) {
		double tenkan = (getHighestHigh(key, 9, barsAgo) + getLowestLow(key, 9, barsAgo)) / 2;
		double kijun = (getHighestHigh(key, 26, barsAgo) + getLowestLow(key, 26, barsAgo)) / 2;
		double pastTenkan = (getHighestHigh(key, 9, barsAgo + 26) + getLowestLow(key, 9, barsAgo + 26)) / 2;
		double pastKijun = (getHighestHigh(key, 26, barsAgo + 26) + getLowestLow(key, 26, barsAgo + 26)) / 2;
		double senkouA = (pastTenkan + pastKijun) / 2;
		double senkouB = (getHighestHigh(key, 52, barsAgo + 26) + getLowestLow(key, 52, barsAgo + 26)) / 2;
		return new double[]{tenkan, kijun, senkouA, senkouB};
	}

	private boolean checkSqueeze(String key, int lookback, double currentBandWidth, int barsAgo) {
		double minBw = Double.MAX_VALUE;
		for (int i = 1; i <= lookback; i++) {
			double bw = (4 * getStdDev(key, 20, barsAgo + i)) / getPastMA(key, 20, barsAgo + i);
			if (bw < minBw) minBw = bw;
		}
		return currentBandWidth <= minBw * 1.5; 
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

	private boolean isMTopFormation(String key, CandleData current, double currentUpper2) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < 20) return false;
		double highest = 0; int highestIdx = -1;
		for (int i = 1; i <= 20; i++) {
			double h = hist.get(hist.size() - i).getHigh();
			if (h > highest) { highest = h; highestIdx = i; }
		}
		if (highestIdx == -1) return false;
		double pastUpper2 = getPastMA(key, 20, highestIdx) + 2 * getStdDev(key, 20, highestIdx);
		return current.getHigh() >= highest * 0.999 && current.getClose() < currentUpper2 && highest >= pastUpper2;
	}

	private boolean isWBottomFormation(String key, CandleData current, double currentLower2) {
		List<CandleData> hist = historyMap.get(key);
		if (hist == null || hist.size() < 20) return false;
		double lowest = Double.MAX_VALUE; int lowestIdx = -1;
		for (int i = 1; i <= 20; i++) {
			double l = hist.get(hist.size() - i).getLow();
			if (l < lowest) { lowest = l; lowestIdx = i; }
		}
		if (lowestIdx == -1) return false;
		double pastLower2 = getPastMA(key, 20, lowestIdx) - 2 * getStdDev(key, 20, lowestIdx);
		return current.getLow() <= lowest * 1.001 && current.getClose() > currentLower2 && lowest <= pastLower2;
	}

	// ==========================================
	// 既存ユーティリティ
	// ==========================================

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
			sumSq += Math.pow(cur.getClose() - sma, 2);
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
			tradeSize = calculateLotSize(candle.getClose());
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
		} else {
			entryPriceMap.remove(key); positionSizeMap.remove(key);
			entryStrategyMap.remove(key); entryCandleTimeMap.remove(key);
		}
	}

	private double calculateCurrentMA(String key, int period) {
		List<CandleData> hist = historyMap.get(key); CandleData cur = currentCandleMap.get(key);
		if (hist == null || hist.size() < period - 1) return 0;
		double sum = cur.getClose();
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

	private double getHighestHigh(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key); if (hist == null || hist.size() < period + barsAgo) return Double.MAX_VALUE;
		double highest = 0; int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) highest = Math.max(highest, hist.get(endIndex - i).getHigh());
		return highest;
	}

	private double getLowestLow(String key, int period, int barsAgo) {
		List<CandleData> hist = historyMap.get(key); if (hist == null || hist.size() < period + barsAgo) return 0.0;
		double lowest = Double.MAX_VALUE; int endIndex = hist.size() - barsAgo;
		for (int i = 0; i < period; i++) lowest = Math.min(lowest, hist.get(endIndex - i).getLow());
		return lowest;
	}

	private double getPastCandleClose(String key, int barsAgo) {
		List<CandleData> hist = historyMap.get(key); if (hist == null || hist.size() < barsAgo) return 0.0;
		return hist.get(hist.size() - barsAgo).getClose();
	}
}