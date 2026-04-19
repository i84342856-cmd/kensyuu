package com.example.cryptotool.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cryptotool.entity.TradeLog;

@Repository
public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    // 最新の取引履歴から順番に100件取得する
    List<TradeLog> findTop100ByOrderByTimeDesc();
    
    // ★追加：起動時の記憶復元用（指定した通貨・時間足の最新の1件を取得）
    Optional<TradeLog> findFirstBySymbolAndTimeframeOrderByTimeDesc(String symbol, String timeframe);
}