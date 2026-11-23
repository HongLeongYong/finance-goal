package com.example.finance.web;

import com.example.finance.form.ContributionForm;
import com.example.finance.form.GoalForm;
import com.example.finance.model.GoalMode;
import com.example.finance.model.GoalTargetType;
import com.example.finance.model.PrincipalMode;
import com.example.finance.service.GoalService;
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
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;
    private final Clock clock;

    public GoalController(GoalService goalService, Clock clock) {
        this.goalService = goalService;
        this.clock = clock;
    }

    @ModelAttribute
    public void options(Model model) {
        model.addAttribute("targetTypes", GoalTargetType.values());
        model.addAttribute("modes", GoalMode.values());
        model.addAttribute("principalModes", PrincipalMode.values());
    }

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("goalForm")) {
            model.addAttribute("goalForm", new GoalForm());
        }
        model.addAttribute("results", goalService.evaluateAll());
        model.addAttribute("defaults", goalService.defaults());
        return "goals/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("goalForm") GoalForm form, BindingResult result,
            Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("results", goalService.evaluateAll());
            model.addAttribute("defaults", goalService.defaults());
            model.addAttribute("openForm", true);
            return "goals/list";
        }
        Long id = goalService.save(null, form).getId();
        redirect.addFlashAttribute("message", "已新增目標「" + form.getName() + "」");
        return "redirect:/goals/" + id;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("contributionForm")) {
            ContributionForm f = new ContributionForm();
            f.setContributionDate(LocalDate.now(clock));
            model.addAttribute("contributionForm", f);
        }
        model.addAttribute("r", goalService.evaluate(goalService.goal(id)));
        model.addAttribute("contributionList", goalService.contributions(id));
        return "goals/detail";
    }

    // ===== 累積式：存入 / 提出 =====

    @PostMapping("/{id}/contributions")
    public String addContribution(@PathVariable Long id, @Valid @ModelAttribute("contributionForm") ContributionForm form,
            BindingResult result, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                goalService.addContribution(id, form);
                redirect.addFlashAttribute("message", (form.isDeposit() ? "已存入 " : "已提出 ")
                        + "$" + form.getAmount().stripTrailingZeros().toPlainString() + "（只標記用途，不影響記帳和帳戶餘額）");
                return "redirect:/goals/" + id + "#contributions";
            } catch (IllegalArgumentException e) {
                result.reject("invalid", e.getMessage());
            }
        }
        redirect.addFlashAttribute("org.springframework.validation.BindingResult.contributionForm", result);
        redirect.addFlashAttribute("contributionForm", form);
        return "redirect:/goals/" + id + "#contributions";
    }

    @PostMapping("/{id}/contributions/{cid}/delete")
    public String deleteContribution(@PathVariable Long id, @PathVariable Long cid, RedirectAttributes redirect) {
        try {
            goalService.deleteContribution(id, cid);
            redirect.addFlashAttribute("message", "已刪除這筆紀錄");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "不能刪除：" + e.getMessage());
        }
        return "redirect:/goals/" + id + "#contributions";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("goalForm", GoalService.toForm(goalService.goal(id)));
        model.addAttribute("goalId", id);
        model.addAttribute("defaults", goalService.defaults());
        return "goals/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("goalForm") GoalForm form,
            BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("goalId", id);
            model.addAttribute("defaults", goalService.defaults());
            return "goals/edit";
        }
        goalService.save(id, form);
        redirect.addFlashAttribute("message", "已更新目標");
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        goalService.delete(id);
        redirect.addFlashAttribute("message", "已刪除目標");
        return "redirect:/goals";
    }
}
