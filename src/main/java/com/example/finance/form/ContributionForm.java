package com.example.finance.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class ContributionForm {

    /** true = 存入，false = 提出 */
    private boolean deposit = true;

    @NotNull(message = "請選擇日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate contributionDate;

    @NotNull(message = "請輸入金額")
    @DecimalMin(value = "1", message = "金額要大於 0")
    private BigDecimal amount;

    @Size(max = 200)
    private String note;

    public boolean isDeposit() { return deposit; }
    public void setDeposit(boolean deposit) { this.deposit = deposit; }
    public LocalDate getContributionDate() { return contributionDate; }
    public void setContributionDate(LocalDate contributionDate) { this.contributionDate = contributionDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
