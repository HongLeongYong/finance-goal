package com.example.finance.web;

import com.example.finance.form.DividendForm;
import com.example.finance.form.TradeForm;
import com.example.finance.model.Profile;
import com.example.finance.model.TradeType;
import com.example.finance.repository.StockPriceRepository;
import com.example.finance.service.AssetService;
import com.example.finance.service.ProfileService;
import com.example.finance.stock.PortfolioService;
import com.example.finance.stock.QuoteService;
import com.example.finance.stock.StockService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/stocks")
public class StockController {

    private final StockService stockService;
    private final PortfolioService portfolioService;
    private final QuoteService quoteService;
    private final AssetService assetService;
    private final ProfileService profileService;
    private final StockPriceRepository priceRepository;
    private final Clock clock;

    public StockController(StockService stockService, PortfolioService portfolioService, QuoteService quoteService,
            AssetService assetService, ProfileService profileService, StockPriceRepository priceRepository, Clock clock) {
        this.stockService = stockService;
        this.portfolioService = portfolioService;
        this.quoteService = quoteService;
        this.assetService = assetService;
        this.profileService = profileService;
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    @GetMapping
    public String overview(Model model) {
        if (!model.containsAttribute("tradeForm")) {
            TradeForm f = new TradeForm();
            f.setTradeDate(LocalDate.now(clock));
            model.addAttribute("tradeForm", f);
        }
        if (!model.containsAttribute("dividendForm")) {
            DividendForm f = new DividendForm();
            f.setPayDate(LocalDate.now(clock));
            model.addAttribute("dividendForm", f);
        }
        addPageData(model);
        return "stocks/overview";
    }

    private void addPageData(Model model) {
        model.addAttribute("portfolio", portfolioService.portfolio());
        model.addAttribute("trades", stockService.trades());
        model.addAttribute("dividends", stockService.dividends());
        model.addAttribute("accounts", assetService.activeAccounts().stream()
                .filter(a -> !a.getType().isLiability()).toList());
        model.addAttribute("lastFetch", priceRepository.findFirstByOrderByFetchedAtDesc().orElse(null));
        model.addAttribute("today", LocalDate.now(clock));
        Profile p = profileService.get();
        model.addAttribute("feeDiscount", p.getFeeDiscount());
        model.addAttribute("minFee", p.getMinFee());
        model.addAttribute("tradeTypes", TradeType.values());
    }

    /** 輸入代號時，前端用來查名稱和最新收盤價 */
    @GetMapping("/lookup")
    @ResponseBody
    public Map<String, Object> lookup(@RequestParam String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        quoteService.lookup(code).ifPresentOrElse(q -> {
            body.put("found", true);
            body.put("code", q.code());
            body.put("name", q.name());
            body.put("market", q.market().getLabel());
            body.put("close", q.close());
            body.put("date", q.date().toString());
            body.put("etf", q.code().startsWith("00"));
        }, () -> body.put("found", false));
        return body;
    }

    @PostMapping("/trades")
    public String addTrade(@Valid @ModelAttribute("tradeForm") TradeForm form, BindingResult result,
            Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                var t = stockService.addTrade(form);
                redirect.addFlashAttribute("message", "已記錄" + t.getType().getLabel() + " " + t.getStock().getCode()
                        + " " + t.getStock().getName() + " " + t.getShares() + " 股");
                return "redirect:/stocks";
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        model.addAttribute("openTradeForm", true);
        if (!model.containsAttribute("dividendForm")) {
            model.addAttribute("dividendForm", new DividendForm());
        }
        addPageData(model);
        return "stocks/overview";
    }

    @PostMapping("/trades/{id}/delete")
    public String deleteTrade(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            stockService.deleteTrade(id);
            redirect.addFlashAttribute("message", "已刪除交易紀錄，交割帳戶餘額已調回");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "不能刪除：" + e.getMessage() + "。請先刪掉之後的賣出紀錄。");
        }
        return "redirect:/stocks#trades";
    }

    @PostMapping("/dividends")
    public String addDividend(@Valid @ModelAttribute("dividendForm") DividendForm form, BindingResult result,
            Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                stockService.addDividend(form);
                redirect.addFlashAttribute("message", "已記錄股利，並在記帳新增一筆「股利」收入");
                return "redirect:/stocks#dividends";
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        model.addAttribute("openDividendForm", true);
        if (!model.containsAttribute("tradeForm")) {
            model.addAttribute("tradeForm", new TradeForm());
        }
        addPageData(model);
        return "stocks/overview";
    }

    @PostMapping("/dividends/{id}/delete")
    public String deleteDividend(@PathVariable Long id, RedirectAttributes redirect) {
        stockService.deleteDividend(id);
        redirect.addFlashAttribute("message", "已刪除股利（記帳裡對應的收入也一起刪除）");
        return "redirect:/stocks#dividends";
    }

    @PostMapping("/prices/refresh")
    public String refreshPrices(RedirectAttributes redirect) {
        QuoteService.RefreshResult r = quoteService.refreshAll();
        stockService.refreshSnapshot();
        redirect.addFlashAttribute(r.errors().isEmpty() ? "message" : "error", r.message());
        return "redirect:/stocks";
    }

    @PostMapping("/{code}/price")
    public String manualPrice(@PathVariable String code, @RequestParam BigDecimal close,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            RedirectAttributes redirect) {
        try {
            if (close.signum() <= 0) {
                throw new IllegalArgumentException("價格要大於 0");
            }
            stockService.setManualPrice(code, date == null ? LocalDate.now(clock) : date, close);
            redirect.addFlashAttribute("message", code + " 已手動設定價格 " + close.stripTrailingZeros().toPlainString());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/stocks";
    }
}
