package com.example.finance.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class DividendForm {

    @NotBlank(message = "請選擇股票")
    private String code;

    @NotNull(message = "請選擇發放日")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate payDate;

    @NotNull(message = "請輸入實際入帳金額")
    @DecimalMin(value = "1", message = "金額要大於 0")
    private BigDecimal amount;

    private Long accountId;

    @Size(max = 200)
    private String note;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public LocalDate getPayDate() { return payDate; }
    public void setPayDate(LocalDate payDate) { this.payDate = payDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
