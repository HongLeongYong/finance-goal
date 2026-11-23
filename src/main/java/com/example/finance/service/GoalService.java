package com.example.finance.service;

import com.example.finance.form.ContributionForm;
import com.example.finance.form.GoalForm;
import com.example.finance.model.Goal;
import com.example.finance.model.GoalContribution;
import com.example.finance.model.PrincipalMode;
import com.example.finance.model.GoalMode;
import com.example.finance.repository.GoalContributionRepository;
import com.example.finance.repository.GoalRepository;
import com.example.finance.service.DepositCalculator.DepositView;
import com.example.finance.service.GoalCalculator.YearPoint;
import com.example.finance.service.LedgerService.CategoryRow;
import com.example.finance.service.LedgerService.MonthReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 目標：新增 / 修改 / 刪除，以及依你的資產和記帳資料試算。 */
@Service
public class GoalService {

    /** 計算「每月可存」看最近幾個月 */
    static final int SAVING_LOOKBACK_MONTHS = 3;
    /** 這些是固定必要支出，不建議從這裡省 */
    static final Set<String> ESSENTIAL_CATEGORIES = Set.of("居住", "保險", "醫療", "孝親");
    static final double CUT_RATIO = 0.3;
    /** 組合方案：年報酬多拉高幾個百分點 */
    static final double COMBO_RETURN_BOOST = 0.02;
    /** 年報酬超過這個值就提醒風險 */
    static final double HIGH_RISK_RETURN = 0.10;
    /** 股票（台股）長期預期年報酬（%），用來算資產加權報酬 */
    public static final BigDecimal STOCK_EXPECTED_RETURN_PCT = BigDecimal.valueOf(5);

    private final GoalRepository goalRepository;
    private final NetWorthService netWorthService;
    private final LedgerService ledgerService;
    private final ProfileService profileService;
    private final GoalContributionRepository contributionRepository;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, NetWorthService netWorthService, LedgerService ledgerService,
            ProfileService profileService, GoalContributionRepository contributionRepository, Clock clock) {
        this.contributionRepository = contributionRepository;
        this.goalRepository = goalRepository;
        this.netWorthService = netWorthService;
        this.ledgerService = ledgerService;
        this.profileService = profileService;
        this.clock = clock;
    }

    // ===== CRUD =====

    @Transactional(readOnly = true)
    public List<Goal> goals() {
        return goalRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Goal goal(Long id) {
        return goalRepository.findById(id).orElseThrow(() -> new NotFoundException("目標", id));
    }

    @Transactional
    public Goal save(Long id, GoalForm f) {
        Goal g = id == null ? new Goal() : goal(id);
        g.setName(f.getName().trim());
        g.setTargetType(f.getTargetType());
        g.setTargetAmount(f.getTargetAmount());
        g.setMonthlyPassiveIncome(f.getMonthlyPassiveIncome());
        g.setWithdrawalRate(f.getWithdrawalRate());
        g.setMode(f.getMode());
        g.setYears(f.getYears());
        g.setPrincipalMode(f.getPrincipalMode());
        g.setPrincipalOverride(f.getPrincipalMode() == PrincipalMode.MANUAL ? f.getPrincipalOverride() : null);
        g.setMonthlySavingOverride(f.getMonthlySavingOverride());
        g.setAnnualReturnOverride(f.getAnnualReturnOverride());
        g.setInflationRate(f.getInflationRate());
        return goalRepository.save(g);
    }

    @Transactional
    public void delete(Long id) {
        goalRepository.delete(goal(id));
    }

    // ===== 累積式：存入 / 提出紀錄（只標記用途，不動記帳和帳戶餘額）=====

    @Transactional(readOnly = true)
    public List<GoalContribution> contributions(Long goalId) {
        return contributionRepository.findByGoalIdOrderByContributionDateDescIdDesc(goalId);
    }

    @Transactional
    public GoalContribution addContribution(Long goalId, ContributionForm f) {
        Goal g = goal(goalId);
        GoalContribution c = new GoalContribution();
        c.setGoal(g);
        c.setContributionDate(f.getContributionDate());
        c.setAmount(f.isDeposit() ? f.getAmount() : f.getAmount().negate());
        c.setNote(f.getNote() == null || f.getNote().isBlank() ? null : f.getNote().trim());
        List<GoalContribution> all = new ArrayList<>(contributionRepository.findByGoalIdOrderByContributionDateAscIdAsc(goalId));
        all.add(c);
        checkNeverNegative(all);
        // 新增存入紀錄時，自動把本金切成「累積式」
        if (g.getPrincipalMode() != PrincipalMode.CONTRIBUTIONS) {
            g.setPrincipalMode(PrincipalMode.CONTRIBUTIONS);
        }
        return contributionRepository.save(c);
    }

    @Transactional
    public void deleteContribution(Long goalId, Long contributionId) {
        GoalContribution c = contributionRepository.findById(contributionId)
                .filter(x -> x.getGoal().getId().equals(goalId))
                .orElseThrow(() -> new NotFoundException("存入紀錄", contributionId));
        List<GoalContribution> rest = contributionRepository.findByGoalIdOrderByContributionDateAscIdAsc(goalId).stream()
                .filter(x -> !x.getId().equals(contributionId)).toList();
        checkNeverNegative(rest);
        contributionRepository.delete(c);
    }

    /** 依日期累加，任何時間點都不能提出超過已存的錢 */
    static void checkNeverNegative(List<GoalContribution> list) {
        List<GoalContribution> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparing(GoalContribution::getContributionDate));
        BigDecimal running = BigDecimal.ZERO;
        for (GoalContribution c : sorted) {
            running = running.add(c.getAmount());
            if (running.signum() < 0) {
                throw new IllegalArgumentException(c.getContributionDate() + " 時這個目標只存了 "
                        + money(running.subtract(c.getAmount())) + "，不能提出 " + money(c.getAmount().negate()));
            }
        }
    }

    /** 存入紀錄的摘要：累積、本月、近 3 個月平均、逐月累積（畫圖用） */
    public record ContributionSummary(
            BigDecimal total,
            BigDecimal thisMonth,
            List<YearMonth> recentMonths,
            BigDecimal recentAverage,
            List<String> monthLabels,
            List<BigDecimal> monthTotals) {

        static ContributionSummary of(List<GoalContribution> asc, YearMonth now) {
            BigDecimal total = asc.stream().map(GoalContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal thisMonth = asc.stream().filter(c -> YearMonth.from(c.getContributionDate()).equals(now))
                    .map(GoalContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<String> labels = new ArrayList<>();
            List<BigDecimal> totals = new ArrayList<>();
            List<YearMonth> recent = new ArrayList<>();
            BigDecimal recentSum = BigDecimal.ZERO;
            if (!asc.isEmpty()) {
                YearMonth first = YearMonth.from(asc.getFirst().getContributionDate());
                BigDecimal running = BigDecimal.ZERO;
                for (YearMonth m = first; !m.isAfter(now); m = m.plusMonths(1)) {
                    YearMonth month = m;
                    BigDecimal inMonth = asc.stream().filter(c -> YearMonth.from(c.getContributionDate()).equals(month))
                            .map(GoalContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    running = running.add(inMonth);
                    labels.add(m.getYear() + "/" + String.format("%02d", m.getMonthValue()));
                    totals.add(running);
                    if (!m.isBefore(now.minusMonths(SAVING_LOOKBACK_MONTHS - 1))) {
                        recent.add(m);
                        recentSum = recentSum.add(inMonth);
                    }
                }
            }
            BigDecimal avg = recent.isEmpty() ? BigDecimal.ZERO
                    : recentSum.divide(BigDecimal.valueOf(recent.size()), 0, RoundingMode.HALF_UP);
            return new ContributionSummary(total, thisMonth, recent, avg, labels, totals);
        }
    }

    public static GoalForm toForm(Goal g) {
        GoalForm f = new GoalForm();
        f.setName(g.getName());
        f.setTargetType(g.getTargetType());
        f.setTargetAmount(g.getTargetAmount());
        f.setMonthlyPassiveIncome(g.getMonthlyPassiveIncome());
        f.setWithdrawalRate(g.getWithdrawalRate());
        f.setMode(g.getMode());
        f.setYears(g.getYears());
        f.setPrincipalMode(g.getPrincipalMode());
        f.setPrincipalOverride(g.getPrincipalOverride());
        f.setMonthlySavingOverride(g.getMonthlySavingOverride());
        f.setAnnualReturnOverride(g.getAnnualReturnOverride());
        f.setInflationRate(g.getInflationRate());
        return f;
    }

    // ===== 系統自動帶入的試算假設 =====

    /** 從你的資料算出來的預設值 */
    public record Defaults(
            BigDecimal principal,         // 目前淨資產
            BigDecimal monthlySaving,     // 近幾個月平均結餘
            List<YearMonth> savingMonths, // 用了哪幾個月
            BigDecimal annualReturnPct,   // 資產加權年報酬（%）
            Map<String, BigDecimal> avgExpenseByCategory,
            Set<String> oneOffCategories) { // 近幾個月只出現在一個月的分類（一次性支出，不建議從這裡省）

        public String savingBasis() {
            if (savingMonths.isEmpty()) {
                return "還沒有記帳資料";
            }
            return "近 " + savingMonths.size() + " 個月平均（"
                    + String.join("、", savingMonths.stream().map(m -> m.getYear() + "/" + m.getMonthValue()).toList()) + "）";
        }
    }

    @Transactional(readOnly = true)
    public Defaults defaults() {
        NetWorthService.AssetSummary summary = netWorthService.summary();

        // 每月可存 = 近 3 個月（含本月）有記帳的月份，平均（收入 − 支出）
        YearMonth now = YearMonth.now(clock);
        List<YearMonth> used = new ArrayList<>();
        BigDecimal netSum = BigDecimal.ZERO;
        Map<String, BigDecimal> expenseSum = new LinkedHashMap<>();
        Map<String, Integer> expenseMonths = new LinkedHashMap<>();
        for (int i = SAVING_LOOKBACK_MONTHS - 1; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            MonthReport r = ledgerService.report(m);
            if (r.income().signum() == 0 && r.expense().signum() == 0) {
                continue;
            }
            used.add(m);
            netSum = netSum.add(r.net());
            for (CategoryRow row : r.expenseRows()) {
                expenseSum.merge(row.displayName(), row.amount(), BigDecimal::add);
                expenseMonths.merge(row.displayName(), 1, Integer::sum);
            }
        }
        BigDecimal months = BigDecimal.valueOf(Math.max(used.size(), 1));
        BigDecimal monthlySaving = netSum.divide(months, 0, RoundingMode.HALF_UP);
        Map<String, BigDecimal> avgExpense = new LinkedHashMap<>();
        expenseSum.forEach((k, v) -> avgExpense.put(k, v.divide(months, 0, RoundingMode.HALF_UP)));
        Set<String> oneOff = new java.util.HashSet<>();
        expenseMonths.forEach((k, n) -> {
            if (!isRecurring(n, used.size())) {
                oneOff.add(k);
            }
        });

        // 年報酬 = 各資產依金額加權：現金、活存 0%，定存用實際利率，股票以長期 5% 估
        BigDecimal totalAssets = summary.totalAssets();
        BigDecimal weighted = summary.stocks().multiply(STOCK_EXPECTED_RETURN_PCT);
        for (DepositView v : netWorthService.activeDeposits()) {
            weighted = weighted.add(v.assetValue().multiply(v.deposit().getAnnualRate()));
        }
        BigDecimal returnPct = totalAssets.signum() <= 0 ? BigDecimal.ZERO
                : weighted.divide(totalAssets, 3, RoundingMode.HALF_UP);

        return new Defaults(summary.netWorth(), monthlySaving, used, returnPct, avgExpense, oneOff);
    }

    /**
     * 是不是固定會花的分類：觀察期至少 2 個月時，要有 2 個月以上都花過才算；
     * 只出現在 1 個月的（例如 10 月去花蓮那趟旅遊）視為一次性支出，不建議「少花」。
     * 只有 1 個月資料時無法判斷，全部都算。
     */
    static boolean isRecurring(int monthsWithSpend, int monthsObserved) {
        return monthsObserved < 2 || monthsWithSpend >= 2;
    }

    // ===== 試算 =====

    public record Assumptions(
            BigDecimal principal, String principalSource,
            BigDecimal monthlySaving, boolean savingAuto, String savingBasis,
            BigDecimal annualReturnPct, boolean returnAuto,
            BigDecimal inflationPct) {

        double nominalRate() {
            return annualReturnPct.doubleValue() / 100;
        }

        Double inflation() {
            return inflationPct == null ? null : inflationPct.doubleValue() / 100;
        }

        /** 實際拿來計算的年報酬（有通膨時是實質報酬） */
        public double effectiveRate() {
            return GoalCalculator.realRate(nominalRate(), inflation());
        }

        public BigDecimal effectiveRatePct() {
            return BigDecimal.valueOf(effectiveRate() * 100).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record Suggestion(String icon, String title, String detail, boolean warn) {
    }

    public record GoalResult(
            Goal goal,
            Assumptions assumptions,
            BigDecimal target,           // 目標金額（今天的購買力）；PROJECT 可能為 null
            BigDecimal progress,         // 目前本金 ÷ 目標，0 ~ 1；沒有目標為 null
            String headline,
            String subline,
            Integer monthsNeeded,        // 照目前速度幾個月達成；null = 100 年內達不到
            LocalDate reachDate,
            BigDecimal projected,        // PROJECT：N 年後總額
            BigDecimal contributed,      // PROJECT：本金 + 累積存入
            BigDecimal requiredMonthly,  // PLAN：每月要存
            Boolean onTrack,             // PLAN：照現在速度能不能在年限內達成
            List<Suggestion> suggestions,
            List<YearPoint> series,
            ContributionSummary contributions) {  // 累積式目標才有

        GoalResult withContributions(ContributionSummary c) {
            return new GoalResult(goal, assumptions, target, progress, headline, subline, monthsNeeded, reachDate,
                    projected, contributed, requiredMonthly, onTrack, suggestions, series, c);
        }

        /** 距離目標還差多少（不會是負的） */
        public BigDecimal remaining() {
            return target == null ? BigDecimal.ZERO : target.subtract(assumptions.principal()).max(BigDecimal.ZERO);
        }

        /** 這個月「應該」存多少：指定年限模式用每月要存，其他用每月可存 */
        public BigDecimal plannedMonthly() {
            return requiredMonthly != null ? requiredMonthly : assumptions.monthlySaving();
        }

        public boolean hasTarget() {
            return target != null;
        }

        public int progressPercent() {
            return progress == null ? 0 : progress.multiply(BigDecimal.valueOf(100)).min(BigDecimal.valueOf(100))
                    .max(BigDecimal.ZERO).intValue();
        }
    }

    @Transactional(readOnly = true)
    public GoalResult evaluate(Goal g) {
        return evaluate(g, defaults());
    }

    @Transactional(readOnly = true)
    public List<GoalResult> evaluateAll() {
        Defaults d = defaults();
        return goals().stream().map(g -> evaluate(g, d)).toList();
    }

    GoalResult evaluate(Goal g, Defaults d) {
        ContributionSummary cs = g.getPrincipalMode() == PrincipalMode.CONTRIBUTIONS
                ? ContributionSummary.of(contributionRepository.findByGoalIdOrderByContributionDateAscIdAsc(g.getId()),
                        YearMonth.now(clock))
                : null;

        BigDecimal principal;
        String principalSource;
        switch (g.getPrincipalMode()) {
            case MANUAL -> {
                principal = g.getPrincipalOverride() == null ? BigDecimal.ZERO : g.getPrincipalOverride();
                principalSource = "手動輸入";
            }
            case CONTRIBUTIONS -> {
                principal = cs.total();
                principalSource = "存入紀錄累積";
            }
            default -> {
                principal = d.principal();
                principalSource = "淨資產";
            }
        }
        // 每月可存：自訂 > 累積式用近 3 個月平均存入 > 全部記帳的平均結餘
        BigDecimal saving;
        String savingBasis;
        if (g.getMonthlySavingOverride() != null) {
            saving = g.getMonthlySavingOverride();
            savingBasis = "自訂";
        } else if (cs != null && !cs.recentMonths().isEmpty()) {
            saving = cs.recentAverage();
            savingBasis = "近 " + cs.recentMonths().size() + " 個月平均存入這個目標";
        } else {
            saving = d.monthlySaving();
            savingBasis = d.savingBasis();
        }

        Assumptions a = new Assumptions(
                principal, principalSource,
                saving, g.getMonthlySavingOverride() == null,
                savingBasis,
                g.getAnnualReturnOverride() != null ? g.getAnnualReturnOverride() : d.annualReturnPct(), g.getAnnualReturnOverride() == null,
                g.getInflationRate());

        double pv = a.principal().doubleValue();
        double pmt = a.monthlySaving().doubleValue();
        double rate = a.effectiveRate();
        BigDecimal target = g.resolvedTargetAmount();
        double fvTarget = target == null ? 0 : target.doubleValue();
        BigDecimal progress = target == null || target.signum() <= 0 ? null
                : a.principal().max(BigDecimal.ZERO).divide(target, 4, RoundingMode.HALF_UP);

        Integer monthsAtPace = target == null ? null : GoalCalculator.monthsToTarget(pv, pmt, rate, fvTarget);
        LocalDate today = LocalDate.now(clock);
        LocalDate reachDate = monthsAtPace == null ? null : today.plusMonths(monthsAtPace);

        GoalResult result = switch (g.getMode()) {
            case PROJECT -> project(g, a, target, progress, monthsAtPace, reachDate, pv, pmt, rate);
            case TIME_TO_TARGET -> timeToTarget(g, a, target, progress, monthsAtPace, reachDate, pv, pmt, rate);
            case PLAN -> plan(g, a, d, target, progress, monthsAtPace, reachDate, pv, pmt, rate);
        };
        return result.withContributions(cs);
    }

    private GoalResult project(Goal g, Assumptions a, BigDecimal target, BigDecimal progress,
            Integer monthsAtPace, LocalDate reachDate, double pv, double pmt, double rate) {
        int years = g.getYears();
        double fv = GoalCalculator.futureValue(pv, pmt, rate, years * 12);
        double contributed = pv + pmt * years * 12;
        String headline = years + " 年後約有 " + money(fv);
        String subline = "本金 " + money(pv) + " ＋ 累積存入 " + money(pmt * years * 12) + " ＋ 報酬 " + money(fv - contributed);
        List<Suggestion> s = new ArrayList<>();
        if (target != null) {
            if (fv >= target.doubleValue()) {
                s.add(new Suggestion("🎯", "會超過目標", "比目標多 " + money(fv - target.doubleValue())
                        + (monthsAtPace != null ? "，約 " + duration(monthsAtPace) + "就能達成" : ""), false));
            } else {
                s.add(new Suggestion("🎯", "還沒到目標", "距離目標 " + money(target) + " 還差 "
                        + money(target.doubleValue() - fv), true));
            }
        }
        return new GoalResult(g, a, target, progress, headline, subline, monthsAtPace, reachDate,
                bd(fv), bd(contributed), null, null, s, GoalCalculator.yearlySeries(pv, pmt, rate, years), null);
    }

    private GoalResult timeToTarget(Goal g, Assumptions a, BigDecimal target, BigDecimal progress,
            Integer monthsAtPace, LocalDate reachDate, double pv, double pmt, double rate) {
        String headline;
        String subline;
        if (monthsAtPace == null) {
            headline = "照目前速度，100 年內達不到";
            subline = pmt <= 0 ? "每月結餘是 " + money(pmt) + "，要先讓收入大於支出" : "可以試試「指定年限」模式看看要怎麼調整";
        } else if (monthsAtPace == 0) {
            headline = "已經達成了 🎉";
            subline = "目前本金 " + money(pv) + " 已超過目標 " + money(target);
        } else {
            headline = "約需 " + duration(monthsAtPace);
            subline = "預計 " + reachDate.getYear() + " 年 " + reachDate.getMonthValue() + " 月達成";
        }
        int horizon = monthsAtPace == null ? 30 : Math.max(1, Math.min(60, (monthsAtPace + 11) / 12));
        return new GoalResult(g, a, target, progress, headline, subline, monthsAtPace, reachDate,
                null, null, null, null, List.of(), GoalCalculator.yearlySeries(pv, pmt, rate, horizon), null);
    }

    private GoalResult plan(Goal g, Assumptions a, Defaults d, BigDecimal target, BigDecimal progress,
            Integer monthsAtPace, LocalDate reachDate, double pv, double pmt, double rate) {
        int years = g.getYears();
        int months = years * 12;
        double fvTarget = target.doubleValue();
        double required = GoalCalculator.requiredMonthly(pv, rate, months, fvTarget);
        boolean onTrack = GoalCalculator.futureValue(pv, pmt, rate, months) >= fvTarget;
        PayRate payRate = profileService.payRate();
        List<Suggestion> s = new ArrayList<>();
        String headline;
        String subline;

        if (onTrack) {
            headline = "照目前速度就能達成 ✅";
            subline = monthsAtPace == null ? "" : "預計 " + duration(monthsAtPace) + "達成，比目標的 " + years + " 年"
                    + (monthsAtPace < months ? "早 " + duration(months - monthsAtPace) : "剛好");
            s.add(new Suggestion("💰", "每月最少要存 " + money(required),
                    "你現在每月存 " + money(pmt) + "，多出 " + money(pmt - required) + " 可以挪去其他目標", false));
        } else {
            double extra = required - pmt;
            double daily = extra / 30;
            headline = "每月要存 " + money(required);
            subline = "比現在（" + money(pmt) + "）每月多 " + money(extra);

            String work = payRate.isConfigured() ? "，等於每天少工作 " + payRate.describe(bd(daily)).replace("工作 ", "") + "的錢" : "";
            s.add(new Suggestion("💰", "多存：每月多存 " + money(extra),
                    "相當於每天少花 " + money(daily) + work, false));

            // 從花費下手：非必要支出各少花 30%
            List<Map.Entry<String, BigDecimal>> cuttable = d.avgExpenseByCategory().entrySet().stream()
                    .filter(e -> ESSENTIAL_CATEGORIES.stream().noneMatch(name -> e.getKey().endsWith(name)))
                    .filter(e -> !d.oneOffCategories().contains(e.getKey()))
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                    .limit(3).toList();
            for (Map.Entry<String, BigDecimal> e : cuttable) {
                double save = e.getValue().doubleValue() * CUT_RATIO;
                s.add(new Suggestion("🧾", "少花：" + e.getKey() + "少花 30%",
                        "每月平均 " + money(e.getValue()) + "，可省 " + money(save)
                                + (save >= extra ? "，光這一項就夠補上差距 ✅" : "，補上差距的 " + pct(save / extra)), false));
            }

            // 提高報酬
            Double neededReturn = GoalCalculator.requiredAnnualReturn(pv, pmt, months, fvTarget);
            if (neededReturn == null) {
                s.add(new Suggestion("📈", "提高報酬：做不到",
                        "就算年報酬 100% 也達不到，要從存錢或時間下手", true));
            } else {
                double nominal = a.inflation() == null ? neededReturn : (1 + neededReturn) * (1 + a.inflation()) - 1;
                boolean risky = nominal > HIGH_RISK_RETURN;
                s.add(new Suggestion("📈", "提高報酬：年報酬要到 " + pct(nominal),
                        "不多存錢的話，需要的年報酬（目前假設 " + pct(a.nominalRate()) + "）"
                                + (risky ? "。⚠️ 長期超過 10% 風險很高，不建議只靠這招" : ""), risky));
            }

            // 延長時間
            if (monthsAtPace == null) {
                s.add(new Suggestion("⏳", "延長時間：照目前速度 100 年內達不到",
                        pmt <= 0 ? "目前每月沒有結餘，要先讓收入大於支出" : "光靠時間不夠，需要搭配其他方法", true));
            } else {
                s.add(new Suggestion("⏳", "延長時間：維持現狀需要 " + duration(monthsAtPace),
                        "比目標多 " + duration(monthsAtPace - months) + "，預計 " + reachDate.getYear() + " 年 "
                                + reachDate.getMonthValue() + " 月達成", false));
            }

            // 一次投入
            double lump = GoalCalculator.requiredPrincipal(pmt, rate, months, fvTarget) - pv;
            s.add(new Suggestion("🏦", "一次投入：現在多投入 " + money(lump),
                    "其他都不變，現在本金要有 " + money(pv + lump), false));

            // 組合方案：年報酬多 2 個百分點 + 多存一點
            double comboNominal = a.nominalRate() + COMBO_RETURN_BOOST;
            double comboRate = GoalCalculator.realRate(comboNominal, a.inflation());
            double comboPmt = GoalCalculator.requiredMonthly(pv, comboRate, months, fvTarget);
            if (comboPmt < required) {
                s.add(new Suggestion("🔀", "組合：年報酬提高到 " + pct(comboNominal) + "，每月存 " + money(comboPmt),
                        comboPmt <= pmt ? "光提高報酬就夠了，不用多存" : "每月只要多存 " + money(comboPmt - pmt)
                                + "（比單靠多存少 " + money(required - comboPmt) + "）", comboNominal > HIGH_RISK_RETURN));
            }
        }
        return new GoalResult(g, a, target, progress, headline, subline, monthsAtPace, reachDate,
                null, null, bd(required), onTrack, s, GoalCalculator.yearlySeries(pv, Math.max(pmt, required), rate, years), null);
    }

    // ===== 文字格式 =====

    static String money(double v) {
        String s = new DecimalFormat("#,##0").format(Math.abs(Math.round(v)));
        return (v < -0.5 ? "−$" : "$") + s;
    }

    static String money(BigDecimal v) {
        return money(v.doubleValue());
    }

    static String pct(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    static String duration(int months) {
        int y = months / 12;
        int m = months % 12;
        if (y == 0) {
            return m + " 個月";
        }
        return m == 0 ? y + " 年" : y + " 年 " + m + " 個月";
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(0, RoundingMode.HALF_UP);
    }

    /** 給畫面用：哪些模式需要填年數 */
    public static boolean needsYears(GoalMode mode) {
        return mode != GoalMode.TIME_TO_TARGET;
    }
}
