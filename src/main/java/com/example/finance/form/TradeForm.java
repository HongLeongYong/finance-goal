package com.example.finance.form;

import com.example.finance.model.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class TradeForm {

    @NotBlank(message = "請輸入股票代號")
    @Pattern(regexp = "[0-9A-Za-z]{4,6}", message = "代號格式不對，例如 2330、0050、00679B")
    private String code;

    /** 查不到代號時才需要手動填 */
    @Size(max = 50)
    private String name;

    @NotNull
    private TradeType type = TradeType.BUY;

    @NotNull(message = "請選擇日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate tradeDate;

    @NotNull(message = "請輸入股數")
    @Min(value = 1, message = "至少 1 股")
    @Max(value = 10_000_000, message = "股數太大了")
    private Integer shares;

    @NotNull(message = "請輸入成交價")
    @DecimalMin(value = "0.01", message = "價格要大於 0")
    private BigDecimal price;

    /** 空白 = 依設定自動計算 */
    @DecimalMin(value = "0", message = "手續費不能是負的")
    private BigDecimal fee;

    @DecimalMin(value = "0", message = "證交稅不能是負的")
    private BigDecimal tax;

    private Long accountId;

    @Size(max = 200)
    private String note;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TradeType getType() { return type; }
    public void setType(TradeType type) { this.type = type; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public Integer getShares() { return shares; }
    public void setShares(Integer shares) { this.shares = shares; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
