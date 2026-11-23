package com.example.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

/**
 * 你的時間值多少錢：由月薪和上下班時間算出時薪 / 分薪 / 秒薪。
 *
 * <pre>
 * 每天工時 = 下班 − 上班 − 午休
 * 每月工時 = 每月上班天數 × 每天工時
 * 時薪 = 月薪 ÷ 每月工時；分薪 = 時薪 ÷ 60；秒薪 = 分薪 ÷ 60
 * </pre>
 */
public record PayRate(
        BigDecimal monthlySalary,
        int workDaysPerMonth,
        LocalTime workStartTime,
        LocalTime workEndTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime) {

    private static final int SCALE = 6;

    /** 每天實際工作的分鐘數（扣掉午休） */
    public static long workMinutesPerDay(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd) {
        long minutes = Duration.between(start, end).toMinutes();
        if (breakStart != null && breakEnd != null) {
            minutes -= Duration.between(breakStart, breakEnd).toMinutes();
        }
        return Math.max(minutes, 0);
    }

    public boolean hasBreak() {
        return breakStartTime != null && breakEndTime != null;
    }

    public BigDecimal workHoursPerDay() {
        long minutes = workMinutesPerDay(workStartTime, workEndTime, breakStartTime, breakEndTime);
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    public boolean isConfigured() {
        return monthlySalary.signum() > 0 && workDaysPerMonth > 0 && workHoursPerDay().signum() > 0;
    }

    public BigDecimal hoursPerMonth() {
        return workHoursPerDay().multiply(BigDecimal.valueOf(workDaysPerMonth));
    }

    public BigDecimal perHour() {
        return isConfigured() ? monthlySalary.divide(hoursPerMonth(), SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    public BigDecimal perMinute() {
        return perHour().divide(BigDecimal.valueOf(60), SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal perSecond() {
        return perHour().divide(BigDecimal.valueOf(3600), SCALE, RoundingMode.HALF_UP);
    }

    /** 這筆錢等於工作幾分鐘（四捨五入）；還沒設定薪水時回傳 -1 */
    public long workMinutes(BigDecimal amount) {
        if (!isConfigured() || amount == null) {
            return -1;
        }
        return amount.abs().divide(perMinute(), 0, RoundingMode.HALF_UP).longValue();
    }

    public String describe(long amount) {
        return describe(BigDecimal.valueOf(amount));
    }

    /** 例如「工作 18 分鐘」、「工作 2 小時 5 分」、「工作 3.2 天」 */
    public String describe(BigDecimal amount) {
        long minutes = workMinutes(amount);
        if (minutes < 0) {
            return "";
        }
        if (minutes < 1) {
            return "工作不到 1 分鐘";
        }
        if (minutes < 60) {
            return "工作 " + minutes + " 分鐘";
        }
        long minutesPerDay = workMinutesPerDay(workStartTime, workEndTime, breakStartTime, breakEndTime);
        if (minutes >= minutesPerDay * 2) {
            BigDecimal days = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(minutesPerDay), 1, RoundingMode.HALF_UP);
            return "工作 " + days.stripTrailingZeros().toPlainString() + " 天";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? "工作 " + h + " 小時" : "工作 " + h + " 小時 " + m + " 分";
    }
}
