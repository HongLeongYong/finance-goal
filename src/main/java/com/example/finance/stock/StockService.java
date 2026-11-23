package com.example.finance.stock;

import com.example.finance.form.DividendForm;
import com.example.finance.form.EntryForm;
import com.example.finance.form.TradeForm;
import com.example.finance.model.Account;
import com.example.finance.model.Category;
import com.example.finance.model.EntryType;
import com.example.finance.model.Market;
import com.example.finance.model.PriceSource;
import com.example.finance.model.Profile;
import com.example.finance.model.Stock;
import com.example.finance.model.StockDividend;
import com.example.finance.model.StockTrade;
import com.example.finance.model.TradeType;
import com.example.finance.repository.AccountRepository;
import com.example.finance.repository.StockDividendRepository;
import com.example.finance.repository.StockRepository;
import com.example.finance.repository.StockTradeRepository;
import com.example.finance.service.LedgerService;
import com.example.finance.service.NetWorthService;
import com.example.finance.service.NotFoundException;
import com.example.finance.service.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 股票買賣、股利、手動股價。 */
@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockTradeRepository tradeRepository;
    private final StockDividendRepository dividendRepository;
    private final AccountRepository accountRepository;
    private final QuoteService quoteService;
    private final ProfileService profileService;
    private final LedgerService ledgerService;
    private final NetWorthService netWorthService;

    public StockService(StockRepository stockRepository, StockTradeRepository tradeRepository,
            StockDividendRepository dividendRepository, AccountRepository accountRepository, QuoteService quoteService,
            ProfileService profileService, LedgerService ledgerService, NetWorthService netWorthService) {
        this.stockRepository = stockRepository;
        this.tradeRepository = tradeRepository;
        this.dividendRepository = dividendRepository;
        this.accountRepository = accountRepository;
        this.quoteService = quoteService;
        this.profileService = profileService;
        this.ledgerService = ledgerService;
        this.netWorthService = netWorthService;
    }

    @Transactional(readOnly = true)
    public List<StockTrade> trades() {
        return tradeRepository.findAllByOrderByTradeDateDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<StockDividend> dividends() {
        return dividendRepository.findAllByOrderByPayDateDescIdDesc();
    }

    /**
     * 找到或建立股票：先查證交所 / 櫃買中心拿名稱和市場（順便存一筆最新收盤價），
     * 查不到就用表單上的名稱，市場記為「手動報價」。
     */
    Stock ensureStock(String rawCode, String nameFromForm) {
        String code = rawCode.trim().toUpperCase();
        Optional<Stock> existing = stockRepository.findById(code);
        if (existing.isPresent()) {
            return existing.get();
        }
        Optional<Quote> quote = quoteService.lookup(code);
        Stock stock;
        if (quote.isPresent()) {
            Quote q = quote.get();
            stock = new Stock(code, q.name(), q.market());
            stockRepository.save(stock);
            quoteService.savePrice(code, q.date(), q.close(), PriceSource.AUTO);
        } else {
            if (nameFromForm == null || nameFromForm.isBlank()) {
                throw new IllegalArgumentException("查不到代號 " + code + "（可能是網路問題或代號不存在），請手動填股票名稱");
            }
            stock = new Stock(code, nameFromForm.trim(), Market.MANUAL);
            stockRepository.save(stock);
        }
        return stock;
    }

    @Transactional
    public StockTrade addTrade(TradeForm f) {
        Stock stock = ensureStock(f.getCode(), f.getName());
        Profile profile = profileService.get();

        StockTrade t = new StockTrade();
        t.setStock(stock);
        t.setTradeDate(f.getTradeDate());
        t.setType(f.getType());
        t.setShares(f.getShares());
        t.setPrice(f.getPrice());
        BigDecimal gross = t.getGross();
        t.setFee(f.getFee() != null ? f.getFee()
                : StockFees.fee(gross, f.getShares(), profile.getFeeDiscount(), profile.getMinFee()));
        t.setTax(f.getType() == TradeType.BUY ? BigDecimal.ZERO
                : f.getTax() != null ? f.getTax() : StockFees.sellTax(gross, stock.isEtf()));
        t.setNote(f.getNote() == null || f.getNote().isBlank() ? null : f.getNote().trim());

        // 先確認加入這筆之後，每個時間點的持股都不會變成負的
        List<StockTrade> all = new ArrayList<>(tradeRepository.findAllByOrderByTradeDateAscIdAsc());
        all.add(t);
        all.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()) != 0
                ? a.getTradeDate().compareTo(b.getTradeDate()) : (a.getId() == null ? 1 : b.getId() == null ? -1 : a.getId().compareTo(b.getId())));
        validate(all);

        if (f.getAccountId() != null) {
            Account acc = settlementAccount(f.getAccountId());
            t.setAccount(acc);
            acc.adjust(t.getCashFlow());
        }
        tradeRepository.save(t);
        netWorthService.refreshCurrentMonth();
        return t;
    }

    @Transactional
    public void deleteTrade(Long id) {
        StockTrade t = tradeRepository.findById(id).orElseThrow(() -> new NotFoundException("交易紀錄", id));
        List<StockTrade> rest = tradeRepository.findAllByOrderByTradeDateAscIdAsc().stream()
                .filter(x -> !x.getId().equals(id)).toList();
        validate(rest);
        if (t.getAccount() != null) {
            t.getAccount().adjust(t.getCashFlow().negate());
        }
        tradeRepository.delete(t);
        netWorthService.refreshCurrentMonth();
    }

    private static void validate(List<StockTrade> tradesAsc) {
        try {
            PortfolioService.holdings(tradesAsc, code -> null, null);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private Account settlementAccount(Long id) {
        Account acc = accountRepository.findById(id).orElseThrow(() -> new NotFoundException("帳戶", id));
        if (acc.getType().isLiability()) {
            throw new IllegalArgumentException("交割帳戶不能是負債帳戶");
        }
        return acc;
    }

    // ===== 股利 =====

    @Transactional
    public StockDividend addDividend(DividendForm f) {
        Stock stock = stockRepository.findById(f.getCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("還沒有這檔股票的交易紀錄"));
        Category dividendCategory = ledgerService.categories(EntryType.INCOME).stream()
                .filter(c -> c.getName().equals("股利")).findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到「股利」收入分類，請到設定新增"));

        // 同時記一筆「股利」收入，月報表和儲蓄率才算得到
        EntryForm entry = new EntryForm();
        entry.setEntryDate(f.getPayDate());
        entry.setType(EntryType.INCOME);
        entry.setAmount(f.getAmount());
        entry.setCategoryId(dividendCategory.getId());
        entry.setAccountId(f.getAccountId());
        entry.setNote("股利｜" + stock.getCode() + " " + stock.getName());
        Long entryId = ledgerService.saveEntry(null, entry).getId();

        StockDividend d = new StockDividend();
        d.setStock(stock);
        d.setPayDate(f.getPayDate());
        d.setAmount(f.getAmount());
        d.setLedgerEntryId(entryId);
        d.setNote(f.getNote() == null || f.getNote().isBlank() ? null : f.getNote().trim());
        return dividendRepository.save(d);
    }

    @Transactional
    public void deleteDividend(Long id) {
        StockDividend d = dividendRepository.findById(id).orElseThrow(() -> new NotFoundException("股利紀錄", id));
        if (d.getLedgerEntryId() != null) {
            try {
                ledgerService.deleteEntry(d.getLedgerEntryId());
            } catch (NotFoundException ignored) {
                // 記帳那筆已經被手動刪掉了
            }
        }
        dividendRepository.delete(d);
        netWorthService.refreshCurrentMonth();
    }

    // ===== 手動股價 =====

    @Transactional
    public void setManualPrice(String code, LocalDate date, BigDecimal close) {
        if (!stockRepository.existsById(code)) {
            throw new IllegalArgumentException("沒有股票 " + code + " 的資料");
        }
        quoteService.savePrice(code, date, close, PriceSource.MANUAL);
        netWorthService.refreshCurrentMonth();
    }

    @Transactional
    public void refreshSnapshot() {
        netWorthService.refreshCurrentMonth();
    }
}
