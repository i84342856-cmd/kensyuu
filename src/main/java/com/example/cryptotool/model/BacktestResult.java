package com.example.cryptotool.model;

/**
 * バックテストのシミュレーション結果を保持するレコード
 */
public record BacktestResult(
    String strategyName,
    int totalTrades,
    int winningTrades,
    int losingTrades,
    double winRate,         // 勝率（%）
    double totalProfit,     // 最終的な純利益（円）
    double maxDrawdown,     // 最大ドローダウン：最も資産が減った時の金額（円）
    double finalBalance     // 最終残高（円）
) {}