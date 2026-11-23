package com.example.finance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

/** 帳戶：現金 / 銀行活存 / 負債。負債的 balance 是「欠多少」（正數）。 */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(length = 200)
    private String note;

    @Convert(converter = NumericBooleanConverter.class)
    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 記一筆收支時調整餘額。
     * 一般帳戶：收入 +、支出 −；負債帳戶（例如信用卡）：支出讓欠款增加、收入（還款）讓欠款減少。
     */
    public void apply(EntryType entryType, BigDecimal amount) {
        boolean increase = (entryType == EntryType.INCOME) != type.isLiability();
        balance = increase ? balance.add(amount) : balance.subtract(amount);
    }

    /** 直接加減餘額（股票交割用：買進扣款、賣出入帳） */
    public void adjust(BigDecimal delta) {
        balance = balance.add(delta);
    }

    public void revert(EntryType entryType, BigDecimal amount) {
        apply(entryType, amount.negate());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
