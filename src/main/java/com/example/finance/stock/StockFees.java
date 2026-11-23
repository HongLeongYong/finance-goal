package com.example.finance.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 台股交易成本。
 * <ul>
 *   <li>手續費 = 成交金額 × 0.1425% × 券商折扣，無條件捨去；整股最低 minFee 元（常見 20），零股最低 1 元</li>
 *   <li>證交稅（只有賣出）= 成交金額 × 0.3%，ETF（代號 00 開頭）0.1%，無條件捨去</li>
 * </ul>
 */
public final class StockFees {

    static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
    static final BigDecimal STOCK_TAX = new BigDecimal("0.003");
    static final BigDecimal ETF_TAX = new BigDecimal("0.001");

    private StockFees() {
    }

    public static BigDecimal fee(BigDecimal gross, int shares, BigDecimal discount, int minFee) {
        BigDecimal raw = gross.multiply(FEE_RATE).multiply(discount).setScale(0, RoundingMode.DOWN);
        int min = shares % 1000 == 0 ? minFee : 1;
        return raw.max(BigDecimal.valueOf(min));
    }

    public static BigDecimal sellTax(BigDecimal gross, boolean etf) {
        return gross.multiply(etf ? ETF_TAX : STOCK_TAX).setScale(0, RoundingMode.DOWN);
    }
}
