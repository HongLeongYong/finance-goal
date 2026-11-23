package com.example.finance.stock;

import com.example.finance.model.Market;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 某檔股票某天的收盤價 */
public record Quote(String code, String name, Market market, BigDecimal close, LocalDate date) {
}
