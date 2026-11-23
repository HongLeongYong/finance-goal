package com.example.finance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

/** 定存。到期日、利息等由 DepositCalculator 算，不存進資料庫。 */
@Entity
@Table(name = "time_deposit")
public class TimeDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String bank;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal;

    /** 年利率（%），例如 1.7 */
    @Column(name = "annual_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal annualRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rate_type", nullable = false, length = 10)
    private RateType rateType = RateType.FIXED;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private PayoutType payout = PayoutType.AT_MATURITY;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "maturity_action", nullable = false, length = 20)
    private MaturityAction maturityAction = MaturityAction.AUTO_RENEW;

    @Convert(converter = NumericBooleanConverter.class)
    @Column(nullable = false)
    private boolean closed;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public BigDecimal getAnnualRate() { return annualRate; }
    public void setAnnualRate(BigDecimal annualRate) { this.annualRate = annualRate; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public int getTermMonths() { return termMonths; }
    public void setTermMonths(int termMonths) { this.termMonths = termMonths; }
    public RateType getRateType() { return rateType; }
    public void setRateType(RateType rateType) { this.rateType = rateType; }
    public PayoutType getPayout() { return payout; }
    public void setPayout(PayoutType payout) { this.payout = payout; }
    public MaturityAction getMaturityAction() { return maturityAction; }
    public void setMaturityAction(MaturityAction maturityAction) { this.maturityAction = maturityAction; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
