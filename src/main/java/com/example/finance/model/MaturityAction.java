package com.example.finance.model;

public enum MaturityAction {
    AUTO_RENEW("本息自動續存"), PRINCIPAL_RENEW("本金續存，利息轉活存"), CLOSE("到期解約");

    private final String label;

    MaturityAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
