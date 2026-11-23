package com.example.finance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class Stock {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Market market;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Stock() {
    }

    public Stock(String code, String name, Market market) {
        this.code = code;
        this.name = name;
        this.market = market;
    }

    /** ETF（代號 00 開頭）賣出證交稅 0.1%，一般股票 0.3% */
    public boolean isEtf() {
        return code.startsWith("00");
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Market getMarket() { return market; }
    public void setMarket(Market market) { this.market = market; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
