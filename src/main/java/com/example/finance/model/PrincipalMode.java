package com.example.finance.model;

/** 目標的「目前本金」從哪裡來 */
public enum PrincipalMode {
    AUTO("自動：用全部淨資產"),
    MANUAL("手動輸入一個金額"),
    CONTRIBUTIONS("累積式：存入紀錄加總");

    private final String label;

    PrincipalMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
