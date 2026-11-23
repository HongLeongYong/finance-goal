package com.example.finance.form;

import com.example.finance.model.EntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class EntryForm {

    @NotNull(message = "請選擇日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate entryDate;

    @NotNull
    private EntryType type = EntryType.EXPENSE;

    @NotNull(message = "請輸入金額")
    @DecimalMin(value = "0.01", message = "金額要大於 0")
    private BigDecimal amount;

    @NotNull(message = "請選擇分類")
    private Long categoryId;

    /** 可不選 */
    private Long accountId;

    @Size(max = 200)
    private String note;

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public EntryType getType() { return type; }
    public void setType(EntryType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
