package com.example.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.model.Account;
import com.example.finance.model.Market;
import com.example.finance.repository.AccountRepository;
import com.example.finance.repository.LedgerEntryRepository;
import com.example.finance.repository.StockPriceRepository;
import com.example.finance.repository.StockTradeRepository;
import com.example.finance.service.NetWorthService;
import com.example.finance.stock.Quote;
import com.example.finance.stock.QuoteSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 股票流程：用假的股價來源，不連網 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class StockWebTests {

    static final LocalDate PRICE_DAY = LocalDate.of(2026, 11, 17);

    @TestConfiguration
    static class FakeQuotes {
        @Bean
        @Primary
        QuoteSource fakeQuoteSource() {
            return new QuoteSource() {
                @Override
                public List<Quote> fetch(Market market) {
                    return switch (market) {
                        case TWSE -> List.of(new Quote("0050", "元大台灣50", Market.TWSE, new BigDecimal("94.00"), PRICE_DAY),
                                new Quote("2330", "台積電", Market.TWSE, new BigDecimal("2170.00"), PRICE_DAY));
                        case TPEX -> List.of(new Quote("6488", "環球晶", Market.TPEX, new BigDecimal("546.00"), PRICE_DAY));
                        case MANUAL -> List.of();
                    };
                }

                /** 個股日成交資訊比 OpenAPI 多一天：只有台積電有 11/18 的價格 */
                @Override
                public Optional<Quote> latestDaily(String code, Market market, LocalDate today) {
                    return code.equals("2330")
                            ? Optional.of(new Quote("2330", "", Market.TWSE, new BigDecimal("2175.00"), PRICE_DAY.plusDays(1)))
                            : Optional.empty();
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired StockTradeRepository trades;
    @Autowired StockPriceRepository prices;
    @Autowired LedgerEntryRepository entries;
    @Autowired NetWorthService netWorth;

    @Test
    void lookupFindsListedAndOtcStocks() throws Exception {
        mvc.perform(get("/stocks/lookup").param("code", "0050"))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.name").value("元大台灣50"))
                .andExpect(jsonPath("$.market").value("上市"));
        mvc.perform(get("/stocks/lookup").param("code", "6488")).andExpect(jsonPath("$.market").value("上櫃"));
        mvc.perform(get("/stocks/lookup").param("code", "1234")).andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void buySellDividendFlow() throws Exception {
        mvc.perform(post("/assets/accounts").param("name", "證券交割").param("type", "BANK").param("balance", "200000"));
        Account acc = accounts.findByArchivedFalseOrderByTypeAscIdAsc().getFirst();

        // 買 1 張 0050 @ 90：手續費 90,000 × 0.1425% × 0.6 = 76.95 → 76
        mvc.perform(post("/stocks/trades").param("type", "BUY").param("code", "0050").param("tradeDate", "2026-11-03")
                        .param("shares", "1000").param("price", "90").param("accountId", acc.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(trades.findAll().getFirst().getFee()).isEqualByComparingTo("76");
        assertThat(accounts.findById(acc.getId()).orElseThrow().getBalance()).isEqualByComparingTo("109924");
        // 查代號時順便存了最新收盤價
        assertThat(prices.findFirstByCodeOrderByPriceDateDesc("0050")).isPresent();

        // 市值 94 × 1000 = 94,000，計入淨資產
        assertThat(netWorth.summary().stocks()).isEqualByComparingTo("94000");
        mvc.perform(get("/stocks"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("元大台灣50")))
                .andExpect(content().string(containsString("$94,000")))
                .andExpect(content().string(containsString("$3,924")));   // 94,000 − 90,076

        // 不能賣超過持有
        mvc.perform(post("/stocks/trades").param("type", "SELL").param("code", "0050").param("tradeDate", "2026-11-10")
                        .param("shares", "2000").param("price", "95"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("只持有 1000 股")));

        // 股利：同時記一筆股利收入，入帳到交割帳戶
        mvc.perform(post("/stocks/dividends").param("code", "0050").param("payDate", "2026-11-15")
                        .param("amount", "1000").param("accountId", acc.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(entries.findAll()).anyMatch(e -> e.getNote().startsWith("股利｜0050"));
        assertThat(accounts.findById(acc.getId()).orElseThrow().getBalance()).isEqualByComparingTo("110924");

        // 賣 500 股 @ 100：ETF 證交稅 0.1% = 50
        mvc.perform(post("/stocks/trades").param("type", "SELL").param("code", "0050").param("tradeDate", "2026-11-12")
                        .param("shares", "500").param("price", "100").param("accountId", acc.getId().toString()))
                .andExpect(status().is3xxRedirection());
        var sell = trades.findAll().stream().filter(t -> t.getType().name().equals("SELL")).findFirst().orElseThrow();
        assertThat(sell.getTax()).isEqualByComparingTo("50");
        assertThat(sell.getFee()).isEqualByComparingTo("42");  // 50,000 × 0.1425% × 0.6 = 42.75 → 42（高於零股最低 1 元）
    }

    @Test
    void refreshAndManualPrice() throws Exception {
        mvc.perform(post("/stocks/trades").param("type", "BUY").param("code", "6488").param("tradeDate", "2026-11-03")
                .param("shares", "100").param("price", "500"));
        mvc.perform(post("/stocks/prices/refresh")).andExpect(status().is3xxRedirection());
        assertThat(prices.findFirstByCodeOrderByPriceDateDesc("6488").orElseThrow().getClose()).isEqualByComparingTo("546");

        mvc.perform(post("/stocks/6488/price").param("close", "560").param("date", "2026-11-18"))
                .andExpect(status().is3xxRedirection());
        assertThat(prices.findFirstByCodeOrderByPriceDateDesc("6488").orElseThrow().getClose()).isEqualByComparingTo("560");
        assertThat(netWorth.summary().stocks()).isEqualByComparingTo("56000");
    }

    @Test
    void unknownCodeNeedsName() throws Exception {
        mvc.perform(post("/stocks/trades").param("type", "BUY").param("code", "1234").param("tradeDate", "2026-11-03")
                        .param("shares", "1000").param("price", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("請手動填股票名稱")));
        mvc.perform(post("/stocks/trades").param("type", "BUY").param("code", "1234").param("name", "未上市公司")
                        .param("tradeDate", "2026-11-03").param("shares", "1000").param("price", "10"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/stocks")).andExpect(content().string(containsString("手動報價")));
    }

    @Test
    void refreshPicksUpSameDayCloseFromStockDay() throws Exception {
        mvc.perform(post("/stocks/trades").param("type", "BUY").param("code", "2330").param("tradeDate", "2026-11-03")
                .param("shares", "10").param("price", "2000"));
        mvc.perform(post("/stocks/trades").param("type", "BUY").param("code", "0050").param("tradeDate", "2026-11-03")
                .param("shares", "100").param("price", "90"));
        mvc.perform(post("/stocks/prices/refresh")).andExpect(status().is3xxRedirection());
        var tsmc = prices.findFirstByCodeOrderByPriceDateDesc("2330").orElseThrow();
        assertThat(tsmc.getPriceDate()).isEqualTo(PRICE_DAY.plusDays(1));
        assertThat(tsmc.getClose()).isEqualByComparingTo("2175");
        // 0050 個股日成交資訊沒有更新，維持 OpenAPI 的 11/17
        assertThat(prices.findFirstByCodeOrderByPriceDateDesc("0050").orElseThrow().getPriceDate()).isEqualTo(PRICE_DAY);
    }
}
