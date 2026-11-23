package com.example.finance.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 目標試算的數學（純計算，不碰資料庫）。每月月底存入，按月複利。
 *
 * <pre>
 * r = 年報酬 ÷ 12，n = 月數，PV = 目前本金，PMT = 每月存入
 * FV  = PV(1+r)^n + PMT × ((1+r)^n − 1) / r
 * 需要幾個月：(1+r)^n = (FV·r + PMT) / (PV·r + PMT)  →  n = ln(…) / ln(1+r)
 * 每月要存：PMT = (FV − PV(1+r)^n) × r / ((1+r)^n − 1)
 * 現在要一次投入多少：PV = (FV − PMT × ((1+r)^n − 1) / r) / (1+r)^n
 * 需要的年報酬：用二分法找出 FV(r) = 目標 的 r
 * </pre>
 *
 * 考慮通膨時改用實質報酬率 (1 + 名目) / (1 + 通膨) − 1，所有金額都以「今天的購買力」表示。
 */
public final class GoalCalculator {

    /** 超過 100 年視為「照這樣存不會達成」 */
    public static final int MAX_MONTHS = 1200;

    private GoalCalculator() {
    }

    public static double realRate(double nominalAnnual, Double inflationAnnual) {
        if (inflationAnnual == null) {
            return nominalAnnual;
        }
        return (1 + nominalAnnual) / (1 + inflationAnnual) - 1;
    }

    private static double growth(double annualRate, int months) {
        return Math.pow(1 + annualRate / 12, months);
    }

    /** 每月存 1 元、存 n 個月的累積倍數：((1+r)^n − 1) / r */
    private static double annuityFactor(double annualRate, int months) {
        double r = annualRate / 12;
        return Math.abs(r) < 1e-12 ? months : (Math.pow(1 + r, months) - 1) / r;
    }

    public static double futureValue(double pv, double pmt, double annualRate, int months) {
        return pv * growth(annualRate, months) + pmt * annuityFactor(annualRate, months);
    }

    /** 達成目標需要的月數；已達成回傳 0；100 年內達不到回傳 null */
    public static Integer monthsToTarget(double pv, double pmt, double annualRate, double target) {
        if (pv >= target) {
            return 0;
        }
        double r = annualRate / 12;
        double n;
        if (Math.abs(r) < 1e-12) {
            if (pmt <= 0) {
                return null;
            }
            n = (target - pv) / pmt;
        } else {
            double denominator = pv * r + pmt;
            double numerator = target * r + pmt;
            if (denominator <= 0 || numerator / denominator <= 0) {
                return null;
            }
            n = Math.log(numerator / denominator) / Math.log(1 + r);
            if (Double.isNaN(n) || n < 0) {
                return null;
            }
        }
        int months = (int) Math.ceil(n - 1e-9);
        return months > MAX_MONTHS ? null : months;
    }

    /** n 個月內達成目標，每月要存多少（已經夠了回傳 0） */
    public static double requiredMonthly(double pv, double annualRate, int months, double target) {
        double needed = (target - pv * growth(annualRate, months)) / annuityFactor(annualRate, months);
        return Math.max(needed, 0);
    }

    /** n 個月內達成目標，現在本金要有多少 */
    public static double requiredPrincipal(double pmt, double annualRate, int months, double target) {
        return (target - pmt * annuityFactor(annualRate, months)) / growth(annualRate, months);
    }

    /** 要多少年報酬才能在 n 個月內達成；100% 年報酬也達不到回傳 null */
    public static Double requiredAnnualReturn(double pv, double pmt, int months, double target) {
        double lo = -0.5;
        double hi = 1.0;
        if (futureValue(pv, pmt, hi, months) < target) {
            return null;
        }
        if (futureValue(pv, pmt, lo, months) >= target) {
            return lo;
        }
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2;
            if (futureValue(pv, pmt, mid, months) >= target) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    /** 逐年資產：總額、其中「本金 + 累積存入」的部分（差額就是報酬） */
    public record YearPoint(int year, double total, double contributed) {
        public double growth() {
            return total - contributed;
        }
    }

    public static List<YearPoint> yearlySeries(double pv, double pmt, double annualRate, int years) {
        List<YearPoint> points = new ArrayList<>();
        for (int y = 0; y <= years; y++) {
            int m = y * 12;
            points.add(new YearPoint(y, futureValue(pv, pmt, annualRate, m), pv + pmt * m));
        }
        return points;
    }
}
