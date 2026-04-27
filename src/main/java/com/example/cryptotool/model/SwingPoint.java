package com.example.cryptotool.model;

/**
 * スイングハイ・スイングロー（高値・安値の山谷）を保持するレコード
 */
public record SwingPoint(int index, double price, boolean isHigh) {
	
}