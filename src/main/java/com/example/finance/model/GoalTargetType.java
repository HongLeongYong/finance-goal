package com.example.finance.model;

public enum GoalTargetType {
    AMOUNT("存到一筆金額"), PASSIVE_INCOME("每月被動收入");

    private final String label;

    GoalTargetType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
