package com.example.finance.model;

public enum Market {
    TWSE("上市"), TPEX("上櫃"), MANUAL("手動報價");

    private final String label;

    Market(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
