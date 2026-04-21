package com.example.cryptotool.model;

import com.example.cryptotool.model.enums.Symbol;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TickData {
    private Symbol symbol;
    private double price;
    private long timestamp; // Unixタイムスタンプ（秒）
}