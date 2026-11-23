package com.example.finance.web;

import com.example.finance.form.CategoryForm;
import com.example.finance.form.ProfileForm;
import com.example.finance.model.EntryType;
import com.example.finance.service.LedgerService;
import com.example.finance.service.ProfileService;
import jakarta.validation.Valid;
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
@RequestMapping("/settings")
public class SettingsController {

    private final ProfileService profileService;
    private final LedgerService ledgerService;

    public SettingsController(ProfileService profileService, LedgerService ledgerService) {
        this.profileService = profileService;
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public String settings(Model model) {
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", ProfileService.toForm(profileService.get()));
        }
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new CategoryForm());
        }
        addCategories(model);
        return "settings/index";
    }

    @PostMapping("/profile")
    public String saveProfile(@Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult result,
            Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("categoryForm", new CategoryForm());
            addCategories(model);
            return "settings/index";
        }
        profileService.update(form);
        redirect.addFlashAttribute("message", "已更新薪資與上下班時間");
        return "redirect:/settings";
    }

    @PostMapping("/categories")
    public String addCategory(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult result,
            RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ledgerService.addCategory(form);
                redirect.addFlashAttribute("message", "已新增分類「" + form.getName() + "」");
                return "redirect:/settings#categories";
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        redirect.addFlashAttribute("org.springframework.validation.BindingResult.categoryForm", result);
        redirect.addFlashAttribute("categoryForm", form);
        return "redirect:/settings#categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            ledgerService.deleteCategory(id);
            redirect.addFlashAttribute("message", "已刪除分類");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings#categories";
    }

    private void addCategories(Model model) {
        model.addAttribute("expenseCategories", ledgerService.categories(EntryType.EXPENSE));
        model.addAttribute("incomeCategories", ledgerService.categories(EntryType.INCOME));
    }
}
