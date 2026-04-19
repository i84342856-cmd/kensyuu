package com.example.cryptotool.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.cryptotool.entity.TradeLog;

@Repository
public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {
    // 最新の取引履歴から順番に100件取得する魔法のメソッド名
    List<TradeLog> findTop100ByOrderByTimeDesc();
}