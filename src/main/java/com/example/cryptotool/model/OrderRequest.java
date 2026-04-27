package com.example.cryptotool.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderRequest {
    private String product_code;      // BTC_JPY など
    private String child_order_type;  // LIMIT (指値) または MARKET (成行)
    private String side;              // BUY または SELL
    private double price;             // 指値の場合の価格
    private double size;              // 数量
    private int minute_to_expire;     // 有効期限（分）
    private String time_in_force;     // GTC, IOC, FOK など
}