package com.example.finance.model;

public enum EntryType {
    INCOME("收入"), EXPENSE("支出");

    private final String label;

    EntryType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
