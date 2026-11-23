package com.example.finance.form;

import com.example.finance.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class AccountForm {

    @NotBlank(message = "請輸入帳戶名稱")
    @Size(max = 50)
    private String name;

    @NotNull(message = "請選擇類型")
    private AccountType type = AccountType.BANK;

    @NotNull(message = "請輸入餘額")
    private BigDecimal balance = BigDecimal.ZERO;

    @Size(max = 200)
    private String note;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
