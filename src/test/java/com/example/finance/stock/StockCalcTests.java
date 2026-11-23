package com.example.finance.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.finance.model.Market;
import com.example.finance.model.Stock;
import com.example.finance.model.StockTrade;
import com.example.finance.model.TradeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockCalcTests {

    @Test
    void feesFollowTaiwanRules() {
        // 1 張 × 100 元 = 100,000；0.1425% × 6 折 = 85.5 → 85
        assertThat(StockFees.fee(new BigDecimal("100000"), 1000, new BigDecimal("0.6"), 20)).isEqualByComparingTo("85");
        // 整股最低 20 元
        assertThat(StockFees.fee(new BigDecimal("10000"), 1000, new BigDecimal("0.6"), 20)).isEqualByComparingTo("20");
        // 零股最低 1 元
        assertThat(StockFees.fee(new BigDecimal("950"), 10, new BigDecimal("0.6"), 20)).isEqualByComparingTo("1");
        // 證交稅：股票 0.3%、ETF 0.1%，無條件捨去
        assertThat(StockFees.sellTax(new BigDecimal("100000"), false)).isEqualByComparingTo("300");
        assertThat(StockFees.sellTax(new BigDecimal("94999"), true)).isEqualByComparingTo("94");
    }

    private static StockTrade trade(Stock s, String date, TradeType type, int shares, String price, String fee, String tax) {
        StockTrade t = new StockTrade();
        t.setStock(s);
        t.setTradeDate(LocalDate.parse(date));
        t.setType(type);
        t.setShares(shares);
        t.setPrice(new BigDecimal(price));
        t.setFee(new BigDecimal(fee));
        t.setTax(new BigDecimal(tax));
        return t;
    }

    @Test
    void averageCostAndRealizedProfit() {
        Stock s = new Stock("0050", "元大台灣50", Market.TWSE);
        List<StockTrade> trades = List.of(
                trade(s, "2026-01-02", TradeType.BUY, 1000, "80", "20", "0"),    // 成本 80,020
                trade(s, "2026-03-02", TradeType.BUY, 1000, "90", "20", "0"),    // 成本 170,040，均價 85.02
                trade(s, "2026-06-02", TradeType.SELL, 500, "100", "20", "50")); // 賣掉成本 42,510
        BigDecimal[] realized = new BigDecimal[1];
        List<PortfolioService.Holding> hs = PortfolioService.holdings(trades, c -> null, realized);
        assertThat(hs).hasSize(1);
        PortfolioService.Holding h = hs.getFirst();
        assertThat(h.shares()).isEqualTo(1500);
        assertThat(h.cost()).isEqualByComparingTo("127530");
        assertThat(h.avgCost()).isEqualByComparingTo("85.02");
        // 50,000 − 20 − 50 − 42,510 = 7,420
        assertThat(realized[0]).isEqualByComparingTo("7420");
        assertThat(h.sharesLabel()).isEqualTo("1 張 500 股");
    }

    @Test
    void cannotSellMoreThanHeld() {
        Stock s = new Stock("2330", "台積電", Market.TWSE);
        List<StockTrade> trades = List.of(
                trade(s, "2026-01-02", TradeType.BUY, 100, "1000", "1", "0"),
                trade(s, "2026-01-03", TradeType.SELL, 200, "1000", "1", "300"));
        assertThatThrownBy(() -> PortfolioService.holdings(trades, c -> null, null))
                .hasMessageContaining("只持有 100 股");
    }

    @Test
    void parsesOpenApiRows() {
        assertThat(OpenApiQuoteSource.rocDate("1151117")).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(OpenApiQuoteSource.decimal("--")).isNull();
        assertThat(OpenApiQuoteSource.decimal("")).isNull();
        List<Quote> quotes = OpenApiQuoteSource.parse(List.of(
                Map.of("Date", "1151117", "Code", "0050", "Name", "元大台灣50", "ClosingPrice", "94.00"),
                Map.of("Date", "1151117", "Code", "9999", "Name", "沒成交", "ClosingPrice", "")),
                Market.TWSE, "Code", "Name", "ClosingPrice");
        assertThat(quotes).hasSize(1);
        assertThat(quotes.getFirst().close()).isEqualByComparingTo("94");
    }

    @Test
    void parsesTwseStockDay() {
        Map<String, Object> body = Map.of("stat", "OK", "data", List.of(
                List.of("115/11/18", "50,794,225", "110,637,335,600", "2,200.00", "2,210.00", "2,150.00", "2,175.00", "+5.00", "180,875", ""),
                List.of("115/11/19", "56,582,314", "120,506,549,868", "2,165.00", "2,190.00", "2,085.00", "2,100.00", "-75.00", "331,916", "")));
        Quote q = OpenApiQuoteSource.parseStockDay("2330", body).orElseThrow();
        assertThat(q.date()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(q.close()).isEqualByComparingTo("2100");
        assertThat(OpenApiQuoteSource.parseStockDay("9999", Map.of("stat", "很抱歉，沒有符合條件的資料!"))).isEmpty();
    }
}
