package com.example.finance.web;

import com.example.finance.service.GoalService;
import com.example.finance.service.LedgerService;
import com.example.finance.service.NetWorthService;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final NetWorthService netWorthService;
    private final LedgerService ledgerService;
    private final GoalService goalService;
    private final Clock clock;

    public DashboardController(NetWorthService netWorthService, LedgerService ledgerService, GoalService goalService,
            Clock clock) {
        this.netWorthService = netWorthService;
        this.ledgerService = ledgerService;
        this.goalService = goalService;
        this.clock = clock;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("summary", netWorthService.summary());
        model.addAttribute("report", ledgerService.report(YearMonth.now(clock)));
        model.addAttribute("recent", ledgerService.recent());
        model.addAttribute("history", netWorthService.history());
        model.addAttribute("goals", goalService.evaluateAll());
        return "dashboard";
    }
}
