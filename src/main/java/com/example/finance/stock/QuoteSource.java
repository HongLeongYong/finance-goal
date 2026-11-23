package com.example.finance.stock;

import com.example.finance.model.Market;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 股價來源。正式環境用證交所 / 櫃買中心 OpenAPI，測試換成假的。 */
public interface QuoteSource {

    /** 抓某個市場「最新一個交易日」全部股票的收盤價 */
    List<Quote> fetch(Market market);

    /**
     * 單一股票最新一個交易日的收盤價（收盤後當天就有，比 fetch 用的 OpenAPI 快一天）。
     * 目前只支援上市；不支援或查不到回傳 empty。
     */
    default Optional<Quote> latestDaily(String code, Market market, LocalDate today) {
        return Optional.empty();
    }
}
