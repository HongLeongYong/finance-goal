package com.example.finance.form;

import com.example.finance.model.MaturityAction;
import com.example.finance.model.PayoutType;
import com.example.finance.model.RateType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class DepositForm {

    @NotBlank(message = "請輸入銀行")
    @Size(max = 50)
    private String bank;

    @NotNull(message = "請輸入本金")
    @DecimalMin(value = "1", message = "本金要大於 0")
    private BigDecimal principal;

    @NotNull(message = "請輸入年利率")
    @DecimalMin(value = "0", message = "利率不能是負的")
    @DecimalMax(value = "20", message = "利率看起來太高了")
    private BigDecimal annualRate;

    @NotNull(message = "請選擇起存日")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Min(value = 1, message = "期數至少 1 個月")
    @Max(value = 120, message = "期數最多 120 個月")
    private int termMonths = 12;

    @NotNull
    private RateType rateType = RateType.FIXED;

    @NotNull
    private PayoutType payout = PayoutType.AT_MATURITY;

    @NotNull
    private MaturityAction maturityAction = MaturityAction.AUTO_RENEW;

    @Size(max = 200)
    private String note;

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
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
