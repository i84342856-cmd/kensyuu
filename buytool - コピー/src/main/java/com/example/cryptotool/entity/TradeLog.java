package com.example.cryptotool.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "trade_logs")
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long time;
    private String symbol;
    private String timeframe;
    private String side;
    private Double price;
    private Double size;
    private String message;
    private Integer strategy;
}