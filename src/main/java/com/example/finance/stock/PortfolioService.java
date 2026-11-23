package com.example.finance.stock;

import com.example.finance.model.Stock;
import com.example.finance.model.StockDividend;
import com.example.finance.model.StockPrice;
import com.example.finance.model.StockTrade;
import com.example.finance.model.TradeType;
import com.example.finance.repository.StockDividendRepository;
import com.example.finance.repository.StockPriceRepository;
import com.example.finance.repository.StockTradeRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 持股與損益（平均成本法），只讀資料庫，不呼叫外部 API。
 *
 * <pre>
 * 買進：持有成本 += 股數 × 價格 + 手續費
 * 賣出：賣掉的成本 = 平均成本 × 賣出股數
 *       已實現損益 += 賣出金額 − 手續費 − 證交稅 − 賣掉的成本
 * 未實現損益 = 市值（股數 × 最新收盤價）− 持有成本
 * </pre>
 */
@Service
public class PortfolioService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final StockTradeRepository tradeRepository;
    private final StockPriceRepository priceRepository;
    private final StockDividendRepository dividendRepository;
    private final Clock clock;

    public PortfolioService(StockTradeRepository tradeRepository, StockPriceRepository priceRepository,
            StockDividendRepository dividendRepository, Clock clock) {
        this.tradeRepository = tradeRepository;
        this.priceRepository = priceRepository;
        this.dividendRepository = dividendRepository;
        this.clock = clock;
    }

    public record Holding(Stock stock, int shares, BigDecimal cost, StockPrice price) {

        public BigDecimal avgCost() {
            return shares == 0 ? BigDecimal.ZERO : cost.divide(BigDecimal.valueOf(shares), 2, RoundingMode.HALF_UP);
        }

        /** 沒有任何股價時，先用平均成本估市值 */
        public BigDecimal marketValue() {
            BigDecimal p = price == null ? avgCost() : price.getClose();
            return p.multiply(BigDecimal.valueOf(shares)).setScale(0, RoundingMode.HALF_UP);
        }

        public BigDecimal unrealized() {
            return marketValue().subtract(cost).setScale(0, RoundingMode.HALF_UP);
        }

        public BigDecimal returnRate() {
            return cost.signum() == 0 ? BigDecimal.ZERO : unrealized().divide(cost, 4, RoundingMode.HALF_UP);
        }

        /** 例如「2 張 300 股」 */
        public String sharesLabel() {
            int lots = shares / 1000;
            int odd = shares % 1000;
            if (lots == 0) {
                return odd + " 股";
            }
            return odd == 0 ? lots + " 張" : lots + " 張 " + odd + " 股";
        }
    }

    public record Portfolio(List<Holding> holdings, BigDecimal realized, BigDecimal dividends12m) {

        public BigDecimal marketValue() {
            return holdings.stream().map(Holding::marketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal cost() {
            return holdings.stream().map(Holding::cost).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(0, RoundingMode.HALF_UP);
        }

        public BigDecimal unrealized() {
            return marketValue().subtract(cost());
        }

        public BigDecimal returnRate() {
            return cost().signum() == 0 ? BigDecimal.ZERO : unrealized().divide(cost(), 4, RoundingMode.HALF_UP);
        }

        /** 某檔佔股票市值的比例 */
        public BigDecimal shareOf(Holding h) {
            BigDecimal total = marketValue();
            return total.signum() == 0 ? BigDecimal.ZERO : h.marketValue().divide(total, 4, RoundingMode.HALF_UP);
        }

        /** 最舊的「最新股價日期」，用來提醒價格是不是太久沒更新 */
        public Optional<LocalDate> oldestPriceDate() {
            return holdings.stream().filter(h -> h.price() != null).map(h -> h.price().getPriceDate())
                    .min(Comparator.naturalOrder());
        }
    }

    /** 算出各檔持股（不含已全部賣光的）與已實現損益 */
    public static List<Holding> holdings(List<StockTrade> tradesAsc, Function<String, StockPrice> latestPrice,
            BigDecimal[] realizedOut) {
        Map<String, Stock> stocks = new LinkedHashMap<>();
        Map<String, Integer> shares = new LinkedHashMap<>();
        Map<String, BigDecimal> cost = new LinkedHashMap<>();
        BigDecimal realized = BigDecimal.ZERO;
        for (StockTrade t : tradesAsc) {
            String code = t.getStock().getCode();
            stocks.putIfAbsent(code, t.getStock());
            int held = shares.getOrDefault(code, 0);
            BigDecimal c = cost.getOrDefault(code, BigDecimal.ZERO);
            if (t.getType() == TradeType.BUY) {
                shares.put(code, held + t.getShares());
                cost.put(code, c.add(t.getGross()).add(t.getFee()));
            } else {
                if (t.getShares() > held) {
                    throw new IllegalStateException(code + " 在 " + t.getTradeDate() + " 賣出 " + t.getShares()
                            + " 股，但當時只持有 " + held + " 股");
                }
                BigDecimal soldCost = held == 0 ? BigDecimal.ZERO
                        : c.multiply(BigDecimal.valueOf(t.getShares())).divide(BigDecimal.valueOf(held), MC);
                realized = realized.add(t.getGross().subtract(t.getFee()).subtract(t.getTax()).subtract(soldCost));
                shares.put(code, held - t.getShares());
                cost.put(code, c.subtract(soldCost));
            }
        }
        if (realizedOut != null) {
            realizedOut[0] = realized.setScale(0, RoundingMode.HALF_UP);
        }
        List<Holding> result = new ArrayList<>();
        stocks.forEach((code, stock) -> {
            int s = shares.get(code);
            if (s > 0) {
                result.add(new Holding(stock, s, cost.get(code).setScale(2, RoundingMode.HALF_UP), latestPrice.apply(code)));
            }
        });
        return result;
    }

    @Transactional(readOnly = true)
    public Portfolio portfolio() {
        BigDecimal[] realized = {BigDecimal.ZERO};
        List<Holding> hs = holdings(tradeRepository.findAllByOrderByTradeDateAscIdAsc(),
                code -> priceRepository.findFirstByCodeOrderByPriceDateDesc(code).orElse(null), realized);
        List<Holding> sorted = new ArrayList<>(hs);
        sorted.sort(Comparator.comparing(Holding::marketValue).reversed());
        LocalDate yearAgo = LocalDate.now(clock).minusYears(1);
        BigDecimal dividends = dividendRepository.findByPayDateAfter(yearAgo).stream()
                .map(StockDividend::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Portfolio(sorted, realized[0], dividends);
    }
}
