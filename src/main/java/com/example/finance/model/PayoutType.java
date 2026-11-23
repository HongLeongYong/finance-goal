package com.example.finance.model;

public enum PayoutType {
    MONTHLY("每月領息（存本取息）"), AT_MATURITY("到期領回（整存整付）");

    private final String label;

    PayoutType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
