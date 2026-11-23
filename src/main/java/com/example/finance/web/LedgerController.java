package com.example.finance.web;

import com.example.finance.form.EntryForm;
import com.example.finance.model.EntryType;
import com.example.finance.service.AssetService;
import com.example.finance.service.LedgerService;
import com.example.finance.service.LedgerService.Filter;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ledger")
public class LedgerController {

    private final LedgerService ledgerService;
    private final AssetService assetService;
    private final Clock clock;

    public LedgerController(LedgerService ledgerService, AssetService assetService, Clock clock) {
        this.ledgerService = ledgerService;
        this.assetService = assetService;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) EntryType type,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long accountId,
            Model model) {
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        if (!model.containsAttribute("entryForm")) {
            EntryForm form = new EntryForm();
            LocalDate today = LocalDate.now(clock);
            form.setEntryDate(YearMonth.from(today).equals(m) ? today : m.atDay(1));
            model.addAttribute("entryForm", form);
        }
        model.addAttribute("month", m);
        model.addAttribute("filter", new Filter(type, categoryId, accountId));
        model.addAttribute("entries", ledgerService.entries(m, new Filter(type, categoryId, accountId)));
        model.addAttribute("report", ledgerService.report(m));
        addOptions(model);
        return "ledger/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("entryForm") EntryForm form, BindingResult result,
            RedirectAttributes redirect) {
        String month = form.getEntryDate() == null ? "" : YearMonth.from(form.getEntryDate()).toString();
        if (!result.hasErrors()) {
            try {
                ledgerService.saveEntry(null, form);
                redirect.addFlashAttribute("message", "已記一筆" + form.getType().getLabel());
                // 保留剛剛的類型和日期，方便連續記帳
                EntryForm next = new EntryForm();
                next.setType(form.getType());
                next.setEntryDate(form.getEntryDate());
                next.setAccountId(form.getAccountId());
                redirect.addFlashAttribute("entryForm", next);
                return "redirect:/ledger?month=" + month;
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        redirect.addFlashAttribute("org.springframework.validation.BindingResult.entryForm", result);
        redirect.addFlashAttribute("entryForm", form);
        return "redirect:/ledger" + (month.isEmpty() ? "" : "?month=" + month);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("entryForm")) {
            model.addAttribute("entryForm", LedgerService.toForm(ledgerService.entry(id)));
        }
        model.addAttribute("entryId", id);
        addOptions(model);
        return "ledger/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("entryForm") EntryForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ledgerService.saveEntry(id, form);
                redirect.addFlashAttribute("message", "已更新");
                return "redirect:/ledger?month=" + YearMonth.from(form.getEntryDate());
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        model.addAttribute("entryId", id);
        addOptions(model);
        return "ledger/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        YearMonth month = YearMonth.from(ledgerService.entry(id).getEntryDate());
        ledgerService.deleteEntry(id);
        redirect.addFlashAttribute("message", "已刪除");
        return "redirect:/ledger?month=" + month;
    }

    @GetMapping("/report")
    public String report(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model) {
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        model.addAttribute("month", m);
        model.addAttribute("report", ledgerService.report(m));
        return "ledger/report";
    }

    private void addOptions(Model model) {
        model.addAttribute("expenseCategories", ledgerService.categories(EntryType.EXPENSE));
        model.addAttribute("incomeCategories", ledgerService.categories(EntryType.INCOME));
        model.addAttribute("accounts", assetService.activeAccounts());
    }
}
