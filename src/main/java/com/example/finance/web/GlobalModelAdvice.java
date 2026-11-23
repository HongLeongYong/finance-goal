package com.example.finance.web;

import com.example.finance.service.PayRate;
import com.example.finance.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 每一頁都會用到的資料：分薪（換算工作時間）、目前在哪個選單 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final ProfileService profileService;

    public GlobalModelAdvice(ProfileService profileService) {
        this.profileService = profileService;
    }

    @ModelAttribute("payRate")
    public PayRate payRate() {
        return profileService.payRate();
    }

    @ModelAttribute("nav")
    public String nav(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/ledger/report")) return "report";
        if (uri.startsWith("/ledger")) return "ledger";
        if (uri.startsWith("/assets")) return "assets";
        if (uri.startsWith("/goals")) return "goals";
        if (uri.startsWith("/stocks")) return "stocks";
        if (uri.startsWith("/settings")) return "settings";
        return "dashboard";
    }
}
