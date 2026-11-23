package com.example.finance.model;

public enum AccountType {
    CASH("現金", false), BANK("銀行活存", false), LIABILITY("負債", true);

    private final String label;
    private final boolean liability;

    AccountType(String label, boolean liability) {
        this.label = label;
        this.liability = liability;
    }

    public String getLabel() {
        return label;
    }

    public boolean isLiability() {
        return liability;
    }
}
