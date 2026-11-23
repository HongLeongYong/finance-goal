package com.example.finance.model;

public enum GoalMode {
    PROJECT("存 N 年後，我會有多少？"), TIME_TO_TARGET("存到目標，需要幾年？"), PLAN("指定金額 + 年限 → 產生建議");

    private final String label;

    GoalMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
