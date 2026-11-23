package com.example.finance.form;

import com.example.finance.model.GoalMode;
import com.example.finance.model.GoalTargetType;
import com.example.finance.model.PrincipalMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class GoalForm {

    @NotBlank(message = "請幫目標取個名字")
    @Size(max = 50)
    private String name;

    @NotNull
    private GoalTargetType targetType = GoalTargetType.AMOUNT;

    @DecimalMin(value = "1", message = "目標金額要大於 0")
    private BigDecimal targetAmount;

    @DecimalMin(value = "1", message = "每月被動收入要大於 0")
    private BigDecimal monthlyPassiveIncome;

    @NotNull(message = "請輸入提領率")
    @DecimalMin(value = "0.5", message = "提領率至少 0.5%")
    @DecimalMax(value = "20", message = "提領率最多 20%")
    private BigDecimal withdrawalRate = BigDecimal.valueOf(4);

    @NotNull
    private GoalMode mode = GoalMode.PLAN;

    private Integer years;

    // ===== 試算假設（空白 = 自動帶入）=====

    @NotNull
    private PrincipalMode principalMode = PrincipalMode.AUTO;

    @DecimalMin(value = "0", message = "本金不能是負的")
    private BigDecimal principalOverride;

    private BigDecimal monthlySavingOverride;

    @DecimalMin(value = "-20", message = "年報酬最低 -20%")
    @DecimalMax(value = "50", message = "年報酬最高 50%")
    private BigDecimal annualReturnOverride;

    @DecimalMin(value = "0", message = "通膨率不能是負的")
    @DecimalMax(value = "20", message = "通膨率最高 20%")
    private BigDecimal inflationRate;

    @AssertTrue(message = "請填目標金額（「存 N 年後會有多少」可以不填）")
    public boolean isTargetAmountPresent() {
        return mode == GoalMode.PROJECT || targetType != GoalTargetType.AMOUNT || targetAmount != null;
    }

    @AssertTrue(message = "選了「手動輸入」就要填本金")
    public boolean isPrincipalPresent() {
        return principalMode != PrincipalMode.MANUAL || principalOverride != null;
    }

    @AssertTrue(message = "請填每月想要的被動收入")
    public boolean isPassiveIncomePresent() {
        return targetType != GoalTargetType.PASSIVE_INCOME || monthlyPassiveIncome != null;
    }

    @AssertTrue(message = "這個模式要填年數（1 ~ 60 年）")
    public boolean isYearsValid() {
        if (mode == GoalMode.TIME_TO_TARGET) {
            return years == null || (years >= 1 && years <= 60);
        }
        return years != null && years >= 1 && years <= 60;
    }

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
}
