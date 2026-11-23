package com.example.finance.model;

public enum RateType {
    FIXED("固定"), FLOATING("機動");

    private final String label;

    RateType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
