package com.example.finance.model;

public enum PriceSource {
    AUTO("自動"), MANUAL("手動");

    private final String label;

    PriceSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
