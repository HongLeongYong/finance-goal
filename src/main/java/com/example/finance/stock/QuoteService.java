package com.example.finance.stock;

import com.example.finance.model.Market;
import com.example.finance.model.PriceSource;
import com.example.finance.model.Stock;
import com.example.finance.model.StockPrice;
import com.example.finance.repository.StockPriceRepository;
import com.example.finance.repository.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查股票名稱、抓收盤價。外部資料一律先存進 stock_price，頁面只讀資料庫，
 * 所以 API 掛掉時頁面照常顯示（最壞只是價格停在上一次）。
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    /** 查代號用的清單快取多久 */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final QuoteSource source;
    private final StockRepository stockRepository;
    private final StockPriceRepository priceRepository;
    private final Clock clock;
    private final Map<Market, Cached> cache = new EnumMap<>(Market.class);

    private record Cached(Map<String, Quote> quotes, Instant fetchedAt) {
    }

    public QuoteService(QuoteSource source, StockRepository stockRepository, StockPriceRepository priceRepository,
            Clock clock) {
        this.source = source;
        this.stockRepository = stockRepository;
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    /** 抓整個市場的報價；force = 不管快取，重新下載 */
    synchronized Map<String, Quote> quotes(Market market, boolean force) {
        Cached c = cache.get(market);
        if (!force && c != null && c.fetchedAt().plus(CACHE_TTL).isAfter(clock.instant())) {
            return c.quotes();
        }
        Map<String, Quote> map = source.fetch(market).stream()
                .collect(Collectors.toMap(Quote::code, Function.identity(), (a, b) -> a));
        cache.put(market, new Cached(map, clock.instant()));
        return map;
    }

    /** 用代號查名稱和市場：先查上市（快），找不到再查上櫃（慢，約 30 秒） */
    public Optional<Quote> lookup(String code) {
        String c = code.trim().toUpperCase();
        try {
            Quote q = quotes(Market.TWSE, false).get(c);
            if (q == null) {
                q = quotes(Market.TPEX, false).get(c);
            }
            return Optional.ofNullable(q);
        } catch (RuntimeException e) {
            log.warn("查詢股票代號 {} 失敗：{}", c, e.getMessage());
            return Optional.empty();
        }
    }

    // ===== 存價格 =====

    @Transactional
    public void savePrice(String code, LocalDate date, BigDecimal close, PriceSource source) {
        StockPrice p = priceRepository.findByCodeAndPriceDate(code, date).orElseGet(StockPrice::new);
        // 同一天已經有手動價格，就不要被自動覆蓋
        if (p.getId() != null && p.getSource() == PriceSource.MANUAL && source == PriceSource.AUTO) {
            return;
        }
        p.setCode(code);
        p.setPriceDate(date);
        p.setClose(close);
        p.setSource(source);
        p.setFetchedAt(LocalDateTime.now(clock));
        priceRepository.save(p);
    }

    public record RefreshResult(int updated, List<String> missing, List<String> errors, LocalDate priceDate,
            int sameDay) {

        public String message() {
            if (updated == 0 && missing.isEmpty() && errors.isEmpty()) {
                return "目前沒有持股，不需要更新";
            }
            StringBuilder sb = new StringBuilder("已更新 " + updated + " 檔股價");
            if (priceDate != null) {
                sb.append("（").append(priceDate).append(" 收盤）");
            }
            if (sameDay > 0) {
                sb.append("，其中 ").append(sameDay).append(" 檔用證交所個股日成交資訊補到最新");
            }
            if (!missing.isEmpty()) {
                sb.append("；找不到：").append(String.join("、", missing)).append("（可以手動輸入價格）");
            }
            if (!errors.isEmpty()) {
                sb.append("；").append(String.join("；", errors));
            }
            return sb.toString();
        }
    }

    /**
     * 更新所有股票（上市、上櫃）的收盤價，只抓有用到的市場。
     * 不包在交易裡：下載可能要 30 秒，不要一直佔著資料庫連線（每筆價格各自存）。
     */
    public RefreshResult refreshAll() {
        List<Stock> stocks = stockRepository.findAll();
        int updated = 0;
        List<String> missing = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        LocalDate latest = null;
        for (Market market : List.of(Market.TWSE, Market.TPEX)) {
            List<Stock> inMarket = stocks.stream().filter(s -> s.getMarket() == market).toList();
            if (inMarket.isEmpty()) {
                continue;
            }
            Map<String, Quote> quotes;
            try {
                quotes = quotes(market, true);
            } catch (RuntimeException e) {
                log.warn("抓 {} 股價失敗", market, e);
                errors.add(market.getLabel() + "資料抓取失敗（" + e.getClass().getSimpleName() + "）");
                continue;
            }
            for (Stock s : inMarket) {
                Quote q = quotes.get(s.getCode());
                if (q == null) {
                    missing.add(s.getCode());
                    continue;
                }
                savePrice(s.getCode(), q.date(), q.close(), PriceSource.AUTO);
                updated++;
                latest = latest == null || q.date().isAfter(latest) ? q.date() : latest;
            }
        }
        // OpenAPI 通常晚一天：上市股票再用個股日成交資訊補抓最新收盤價
        int sameDay = 0;
        LocalDate today = LocalDate.now(clock);
        for (Stock s : stocks) {
            if (s.getMarket() != Market.TWSE) {
                continue;
            }
            try {
                Optional<Quote> q = source.latestDaily(s.getCode(), s.getMarket(), today);
                if (q.isEmpty()) {
                    continue;
                }
                LocalDate stored = priceRepository.findFirstByCodeOrderByPriceDateDesc(s.getCode())
                        .map(StockPrice::getPriceDate).orElse(null);
                if (stored == null || q.get().date().isAfter(stored)) {
                    savePrice(s.getCode(), q.get().date(), q.get().close(), PriceSource.AUTO);
                    sameDay++;
                    if (missing.remove(s.getCode())) {
                        updated++;
                    }
                }
                latest = latest == null || q.get().date().isAfter(latest) ? q.get().date() : latest;
            } catch (RuntimeException e) {
                log.warn("個股日成交資訊 {} 抓取失敗：{}", s.getCode(), e.getMessage());
            }
        }
        return new RefreshResult(updated, missing, errors, latest, sameDay);
    }

    /** 交易日下午收盤後自動更新（資料約 14:30 後陸續更新，晚上再補一次） */
    @Scheduled(cron = "${finance.stocks.price-cron}", zone = "Asia/Taipei")
    public void scheduledRefresh() {
        log.info("排程更新股價：{}", refreshAll().message());
    }

    @Scheduled(cron = "${finance.stocks.price-cron-evening}", zone = "Asia/Taipei")
    public void scheduledRefreshEvening() {
        log.info("排程更新股價（晚上補抓）：{}", refreshAll().message());
    }
}
