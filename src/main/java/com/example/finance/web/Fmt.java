package com.example.finance.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/** 樣板裡用 ${@fmt.money(x)} 格式化數字 */
@Component("fmt")
public class Fmt {

    public String money(BigDecimal v) {
        return v == null ? "—" : new DecimalFormat("#,##0").format(v.setScale(0, RoundingMode.HALF_UP));
    }

    /** 指定小數位數，例如秒薪 0.2841 */
    public String money(BigDecimal v, int decimals) {
        if (v == null) {
            return "—";
        }
        String pattern = decimals <= 0 ? "#,##0" : "#,##0." + "0".repeat(decimals);
        return new DecimalFormat(pattern).format(v.setScale(decimals, RoundingMode.HALF_UP));
    }

    /** 帶貨幣符號：1950 → $1,950；−1950 → −$1,950 */
    public String cur(BigDecimal v) {
        return cur(v, 0);
    }

    public String cur(double v) {
        return cur(BigDecimal.valueOf(v), 0);
    }

    public String cur(BigDecimal v, int decimals) {
        if (v == null) {
            return "—";
        }
        return (v.signum() < 0 ? "−$" : "$") + money(v.abs(), decimals);
    }

    /** 0.4523 → 45.2% */
    public String pct(BigDecimal ratio) {
        if (ratio == null) {
            return "—";
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 給 CSS 寬度用：0.4523 → 45.23 */
    public String pctNumber(BigDecimal ratio) {
        return ratio == null ? "0" : ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String rate(BigDecimal annualRatePercent) {
        return annualRatePercent.stripTrailingZeros().toPlainString() + "%";
    }

    public String month(YearMonth m) {
        return m.getYear() + " 年 " + m.getMonthValue() + " 月";
    }
}
