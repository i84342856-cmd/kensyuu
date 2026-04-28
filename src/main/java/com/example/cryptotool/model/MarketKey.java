package com.example.cryptotool.model;

import com.example.cryptotool.model.enums.Symbol;
import com.example.cryptotool.model.enums.TimeFrame;

public record MarketKey(Symbol symbol, TimeFrame timeFrame) {
    @Override
    public String toString() {
        return symbol.name() + "_" + timeFrame.name();
    }
}