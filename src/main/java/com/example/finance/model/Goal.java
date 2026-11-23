package com.example.finance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 目標 = 目標內容（金額或每月被動收入）+ 計算模式 + 試算假設（可覆寫系統預設）。 */
@Entity
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "target_type", nullable = false, length = 20)
    private GoalTargetType targetType = GoalTargetType.AMOUNT;

    @Column(name = "target_amount", precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "monthly_passive_income", precision = 15, scale = 2)
    private BigDecimal monthlyPassiveIncome;

    /** 安全提領率（%），被動收入換算目標金額用 */
    @Column(name = "withdrawal_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal withdrawalRate = BigDecimal.valueOf(4);

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GoalMode mode = GoalMode.PLAN;

    private Integer years;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "principal_mode", nullable = false, length = 15)
    private PrincipalMode principalMode = PrincipalMode.AUTO;

    /** principalMode = MANUAL 時使用 */
    @Column(name = "principal_override", precision = 15, scale = 2)
    private BigDecimal principalOverride;

    @Column(name = "monthly_saving_override", precision = 15, scale = 2)
    private BigDecimal monthlySavingOverride;

    /** % */
    @Column(name = "annual_return_override", precision = 6, scale = 3)
    private BigDecimal annualReturnOverride;

    /** %；null = 不考慮通膨 */
    @Column(name = "inflation_rate", precision = 5, scale = 2)
    private BigDecimal inflationRate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 目標金額（今天的購買力）。
     * 被動收入型：每月被動收入 × 12 ÷ 提領率，例如 3 萬 × 12 ÷ 4% = 900 萬。
     * 沒有設定目標（只看 N 年後有多少）時回傳 null。
     */
    public BigDecimal resolvedTargetAmount() {
        if (targetType == GoalTargetType.PASSIVE_INCOME) {
            if (monthlyPassiveIncome == null || withdrawalRate == null || withdrawalRate.signum() <= 0) {
                return null;
            }
            return monthlyPassiveIncome.multiply(BigDecimal.valueOf(1200))
                    .divide(withdrawalRate, 0, RoundingMode.HALF_UP);
        }
        return targetAmount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public GoalTargetType getTargetType() { return targetType; }
    public void setTargetType(GoalTargetType targetType) { this.targetType = targetType; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getMonthlyPassiveIncome() { return monthlyPassiveIncome; }
    public void setMonthlyPassiveIncome(BigDecimal monthlyPassiveIncome) { this.monthlyPassiveIncome = monthlyPassiveIncome; }
    public BigDecimal getWithdrawalRate() { return withdrawalRate; }
    public void setWithdrawalRate(BigDecimal withdrawalRate) { this.withdrawalRate = withdrawalRate; }
    public GoalMode getMode() { return mode; }
    public void setMode(GoalMode mode) { this.mode = mode; }
    public Integer getYears() { return years; }
    public void setYears(Integer years) { this.years = years; }
    public PrincipalMode getPrincipalMode() { return principalMode; }
    public void setPrincipalMode(PrincipalMode principalMode) { this.principalMode = principalMode; }
    public BigDecimal getPrincipalOverride() { return principalOverride; }
    public void setPrincipalOverride(BigDecimal principalOverride) { this.principalOverride = principalOverride; }
    public BigDecimal getMonthlySavingOverride() { return monthlySavingOverride; }
    public void setMonthlySavingOverride(BigDecimal monthlySavingOverride) { this.monthlySavingOverride = monthlySavingOverride; }
    public BigDecimal getAnnualReturnOverride() { return annualReturnOverride; }
    public void setAnnualReturnOverride(BigDecimal annualReturnOverride) { this.annualReturnOverride = annualReturnOverride; }
    public BigDecimal getInflationRate() { return inflationRate; }
    public void setInflationRate(BigDecimal inflationRate) { this.inflationRate = inflationRate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
