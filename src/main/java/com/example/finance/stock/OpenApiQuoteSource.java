package com.example.finance.stock;

import com.example.finance.model.Market;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 證交所、櫃買中心的官方 OpenAPI（免費、不用 key）。
 * <ul>
 *   <li>上市：openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL（約 1,400 檔，Code / Name / ClosingPrice）</li>
 *   <li>上櫃：tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes（約 1.4 萬筆含權證，約 5MB，下載較慢）</li>
 * </ul>
 * 日期是民國年，例如 1151117 = 2026/11/17。沒有成交的股票收盤價是空白或 "--"，略過。
 *
 * <p>OpenAPI 通常要到隔天才更新，所以上市股票另外用「個股日成交資訊」
 * （www.twse.com.tw/exchangeReport/STOCK_DAY）補抓當天收盤價。這個網址有頻率限制，
 * 每次請求之間至少間隔 {@value #STOCK_DAY_INTERVAL_MS} 毫秒。
 */
@Component
public class OpenApiQuoteSource implements QuoteSource {

    static final String TWSE_URL = "https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL";
    static final String TPEX_URL = "https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes";
    static final String STOCK_DAY_URL = "https://www.twse.com.tw/exchangeReport/STOCK_DAY?response=json&date={date}&stockNo={code}";
    static final long STOCK_DAY_INTERVAL_MS = 1500;
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private long lastStockDayRequest;

    private final RestClient restClient;

    public OpenApiQuoteSource() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public List<Quote> fetch(Market market) {
        return switch (market) {
            case TWSE -> parse(get(TWSE_URL), Market.TWSE, "Code", "Name", "ClosingPrice");
            case TPEX -> parse(get(TPEX_URL), Market.TPEX, "SecuritiesCompanyCode", "CompanyName", "Close");
            case MANUAL -> List.of();
        };
    }

    @Override
    public Optional<Quote> latestDaily(String code, Market market, LocalDate today) {
        if (market != Market.TWSE) {
            return Optional.empty();
        }
        // 先查這個月；月初還沒有交易日時再查上個月
        Optional<Quote> q = latestInMonth(code, today.withDayOfMonth(1));
        return q.isPresent() ? q : latestInMonth(code, today.minusMonths(1).withDayOfMonth(1));
    }

    private Optional<Quote> latestInMonth(String code, LocalDate monthStart) {
        throttle();
        Map<String, Object> body = restClient.get().uri(STOCK_DAY_URL, monthStart.format(YYYYMMDD), code).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        return parseStockDay(code, body);
    }

    private synchronized void throttle() {
        long wait = lastStockDayRequest + STOCK_DAY_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastStockDayRequest = System.currentTimeMillis();
    }

    /**
     * 個股日成交資訊：{"stat":"OK","data":[["115/11/20","成交股數","成交金額","開盤","最高","最低","收盤",...], ...]}
     * 取最後一筆有收盤價的資料。
     */
    static Optional<Quote> parseStockDay(String code, Map<String, Object> body) {
        if (body == null || !"OK".equals(body.get("stat")) || !(body.get("data") instanceof List<?> rows)) {
            return Optional.empty();
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i) instanceof List<?> row && row.size() > 6) {
                LocalDate date = rocDate(str(row.get(0)).replace("/", ""));
                BigDecimal close = decimal(str(row.get(6)));
                if (date != null && close != null) {
                    return Optional.of(new Quote(code, "", Market.TWSE, close, date));
                }
            }
        }
        return Optional.empty();
    }

    private List<Map<String, Object>> get(String url) {
        List<Map<String, Object>> rows = restClient.get().uri(url).retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        return rows == null ? List.of() : rows;
    }

    static List<Quote> parse(List<Map<String, Object>> rows, Market market, String codeKey, String nameKey, String closeKey) {
        List<Quote> quotes = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String code = str(row.get(codeKey));
            BigDecimal close = decimal(str(row.get(closeKey)));
            LocalDate date = rocDate(str(row.get("Date")));
            if (code.isEmpty() || close == null || date == null) {
                continue;
            }
            quotes.add(new Quote(code, str(row.get(nameKey)), market, close, date));
        }
        return quotes;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    static BigDecimal decimal(String s) {
        try {
            BigDecimal v = new BigDecimal(s.replace(",", ""));
            return v.signum() > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 民國日期 1151117 → 2026-11-17 */
    static LocalDate rocDate(String s) {
        if (s.length() < 7) {
            return null;
        }
        try {
            int len = s.length();
            int year = Integer.parseInt(s.substring(0, len - 4)) + 1911;
            int month = Integer.parseInt(s.substring(len - 4, len - 2));
            int day = Integer.parseInt(s.substring(len - 2));
            return LocalDate.of(year, month, day);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
