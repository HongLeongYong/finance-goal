package com.example.finance.web;

import com.example.finance.form.AccountForm;
import com.example.finance.form.DepositForm;
import com.example.finance.model.AccountType;
import com.example.finance.model.MaturityAction;
import com.example.finance.model.PayoutType;
import com.example.finance.model.RateType;
import com.example.finance.service.AssetService;
import com.example.finance.service.NetWorthService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/assets")
public class AssetController {

    private final AssetService assetService;
    private final NetWorthService netWorthService;
    private final Clock clock;

    public AssetController(AssetService assetService, NetWorthService netWorthService, Clock clock) {
        this.assetService = assetService;
        this.netWorthService = netWorthService;
        this.clock = clock;
    }

    @ModelAttribute
    public void options(Model model) {
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("rateTypes", RateType.values());
        model.addAttribute("payoutTypes", PayoutType.values());
        model.addAttribute("maturityActions", MaturityAction.values());
    }

    @GetMapping
    public String overview(Model model) {
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new AccountForm());
        }
        if (!model.containsAttribute("depositForm")) {
            DepositForm f = new DepositForm();
            f.setStartDate(LocalDate.now(clock));
            model.addAttribute("depositForm", f);
        }
        model.addAttribute("summary", netWorthService.summary());
        model.addAttribute("accounts", assetService.activeAccounts());
        model.addAttribute("deposits", netWorthService.activeDeposits());
        model.addAttribute("closedDeposits", assetService.closedDeposits());
        model.addAttribute("history", netWorthService.history());
        return "assets/overview";
    }

    // ===== 帳戶 =====

    @PostMapping("/accounts")
    public String createAccount(@Valid @ModelAttribute("accountForm") AccountForm form, BindingResult result,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("org.springframework.validation.BindingResult.accountForm", result);
            redirect.addFlashAttribute("accountForm", form);
            return "redirect:/assets#accounts";
        }
        assetService.saveAccount(null, form);
        redirect.addFlashAttribute("message", "已新增帳戶「" + form.getName() + "」");
        return "redirect:/assets";
    }

    @GetMapping("/accounts/{id}/edit")
    public String editAccount(@PathVariable Long id, Model model) {
        model.addAttribute("accountForm", AssetService.toForm(assetService.account(id)));
        model.addAttribute("accountId", id);
        return "assets/account-edit";
    }

    @PostMapping("/accounts/{id}")
    public String updateAccount(@PathVariable Long id, @Valid @ModelAttribute("accountForm") AccountForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("accountId", id);
            return "assets/account-edit";
        }
        assetService.saveAccount(id, form);
        redirect.addFlashAttribute("message", "已更新帳戶");
        return "redirect:/assets";
    }

    @PostMapping("/accounts/{id}/delete")
    public String deleteAccount(@PathVariable Long id, RedirectAttributes redirect) {
        assetService.removeAccount(id);
        redirect.addFlashAttribute("message", "已移除帳戶（有記帳紀錄的帳戶會封存保留）");
        return "redirect:/assets";
    }

    // ===== 定存 =====

    @PostMapping("/deposits")
    public String createDeposit(@Valid @ModelAttribute("depositForm") DepositForm form, BindingResult result,
            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("org.springframework.validation.BindingResult.depositForm", result);
            redirect.addFlashAttribute("depositForm", form);
            redirect.addFlashAttribute("openDepositForm", true);
            return "redirect:/assets#deposits";
        }
        assetService.saveDeposit(null, form);
        redirect.addFlashAttribute("message", "已新增定存（" + form.getBank() + "）");
        return "redirect:/assets#deposits";
    }

    @GetMapping("/deposits/{id}/edit")
    public String editDeposit(@PathVariable Long id, Model model) {
        model.addAttribute("depositForm", AssetService.toForm(assetService.deposit(id)));
        model.addAttribute("depositId", id);
        return "assets/deposit-edit";
    }

    @PostMapping("/deposits/{id}")
    public String updateDeposit(@PathVariable Long id, @Valid @ModelAttribute("depositForm") DepositForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("depositId", id);
            return "assets/deposit-edit";
        }
        assetService.saveDeposit(id, form);
        redirect.addFlashAttribute("message", "已更新定存");
        return "redirect:/assets#deposits";
    }

    @PostMapping("/deposits/{id}/close")
    public String closeDeposit(@PathVariable Long id, RedirectAttributes redirect) {
        assetService.closeDeposit(id);
        redirect.addFlashAttribute("message", "定存已標記為結束，不再計入資產。記得把領回的錢記到銀行帳戶。");
        return "redirect:/assets#deposits";
    }

    @PostMapping("/deposits/{id}/delete")
    public String deleteDeposit(@PathVariable Long id, RedirectAttributes redirect) {
        assetService.deleteDeposit(id);
        redirect.addFlashAttribute("message", "已刪除定存");
        return "redirect:/assets#deposits";
    }

    @PostMapping("/snapshot")
    public String snapshot(RedirectAttributes redirect) {
        netWorthService.refreshCurrentMonth();
        redirect.addFlashAttribute("message", "已更新本月淨資產快照");
        return "redirect:/assets";
    }
}
