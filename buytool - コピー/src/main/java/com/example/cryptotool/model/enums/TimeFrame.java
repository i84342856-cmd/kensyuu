package com.example.cryptotool.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TimeFrame {
    M1("1分足", 60),
    M5("5分足", 300),
    M15("15分足", 900),
    M30("30分足", 1800),
    H1("1時間足", 3600),
    D1("日足", 86400),
    W1("週足", 604800);

    private final String description;
    private final int seconds; // 秒数換算（集計ロジックで使用）
}