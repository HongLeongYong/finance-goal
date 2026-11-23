package com.example.finance.model;

public enum TradeType {
    BUY("買進"), SELL("賣出");

    private final String label;

    TradeType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
