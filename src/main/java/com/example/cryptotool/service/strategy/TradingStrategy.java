package com.example.cryptotool.service.strategy;

import com.example.cryptotool.model.MarketKey;
import com.example.cryptotool.model.SignalDecision;
import com.example.cryptotool.model.response.ChartInitResponse.CandleData;
import com.example.cryptotool.service.core.MarketDataStore;

public interface TradingStrategy {
    
    // 戦略のID（ログ記録用）
    int getStrategyId();

    // エントリー（新規注文）の条件を満たしているか判定する
    SignalDecision checkEntry(MarketKey key, CandleData current, MarketDataStore dataStore);

    // イグジット（決済・損切り）の条件を満たしているか判定する
    SignalDecision checkExit(MarketKey key, CandleData current, MarketDataStore dataStore, 
                             String currentPosition, double entryPrice, long entryTime);
}