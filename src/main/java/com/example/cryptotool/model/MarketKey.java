package com.example.cryptotool.model;

import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;

/**
 * 【専用キー】文字列結合によるバグを防ぐための複合キー
 * Mapのキーとして安全に機能するように、Javaのrecordを使用しています。
 */
public record MarketKey(Symbol symbol, TimeFrame timeFrame) {
    // recordを使用することで、equals() や hashCode() が自動で正確に生成され、
    // Mapのキーとして不具合なく動作します。
}