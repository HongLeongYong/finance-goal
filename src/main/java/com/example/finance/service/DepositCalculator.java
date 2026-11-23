package com.example.finance.service;

import com.example.finance.model.MaturityAction;
import com.example.finance.model.PayoutType;
import com.example.finance.model.TimeDeposit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 定存試算（純計算，不碰資料庫）。
 *
 * <ul>
 *   <li>每月領息（存本取息）：每月利息 = 本金 × 年利率 ÷ 12，單利</li>
 *   <li>到期領回（整存整付）：每月複利，到期本息 = 本金 × (1 + 年利率/12)^期數</li>
 *   <li>自動續存：已過到期日就自動滾到「目前這一期」；整存整付的本息續存會把利息併入本金</li>
 *   <li>中途解約（估算）：未滿 1 個月不計息；滿 1 個月以上按實存月數、目前利率打八折，單利計算</li>
 * </ul>
 */
public final class DepositCalculator {

    public static final int MATURING_SOON_DAYS = 30;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);
    private static final BigDecimal EARLY_TERMINATION_FACTOR = new BigDecimal("0.8");
    private static final MathContext MC = MathContext.DECIMAL64;

    private DepositCalculator() {
    }

    public enum Status {
        ACTIVE("存款中"), MATURING_SOON("即將到期"), MATURED("已到期"), CLOSED("已解約");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 某筆定存在 today 這天的試算結果 */
    public record DepositView(
            TimeDeposit deposit,
            int cycle,                    // 第幾期（1 = 原始那一期，自動續存後遞增）
            LocalDate cycleStart,
            LocalDate maturityDate,
            BigDecimal cyclePrincipal,    // 本期本金
            BigDecimal monthlyInterest,   // 每月利息（被動收入用）
            BigDecimal totalInterest,     // 本期到期總利息
            BigDecimal maturityAmount,    // 本期到期本息
            BigDecimal accruedInterest,   // 本期到今天已累積的利息
            BigDecimal earlyTerminationInterest, // 今天解約大約拿到的利息
            long daysToMaturity,
            Status status) {

        /** 計入淨資產的金額：本期本金；已到期待解約的整存整付算到期本息 */
        public BigDecimal assetValue() {
            if (status == Status.CLOSED) {
                return BigDecimal.ZERO;
            }
            if (status == Status.MATURED && deposit.getPayout() == PayoutType.AT_MATURITY) {
                return maturityAmount;
            }
            return cyclePrincipal;
        }

        /** 0 ~ 100，本期經過的時間比例（畫進度條用） */
        public int progressPercent() {
            long total = ChronoUnit.DAYS.between(cycleStart, maturityDate);
            long passed = total - Math.max(daysToMaturity, 0);
            return total <= 0 ? 100 : (int) Math.min(100, Math.max(0, passed * 100 / total));
        }
    }

    public static DepositView calculate(TimeDeposit d, LocalDate today) {
        BigDecimal rate = d.getAnnualRate().divide(BigDecimal.valueOf(100), MC);
        int term = d.getTermMonths();
        BigDecimal principal = d.getPrincipal();
        LocalDate start = d.getStartDate();
        LocalDate maturity = start.plusMonths(term);
        int cycle = 1;

        // 自動續存：已到期就往後滾，直到今天落在某一期裡面
        if (d.getMaturityAction() != MaturityAction.CLOSE && !d.isClosed()) {
            while (!today.isBefore(maturity)) {
                if (d.getMaturityAction() == MaturityAction.AUTO_RENEW && d.getPayout() == PayoutType.AT_MATURITY) {
                    principal = maturityAmount(principal, rate, term, d.getPayout());
                }
                start = maturity;
                maturity = start.plusMonths(term);
                cycle++;
            }
        }

        BigDecimal monthlyInterest = principal.multiply(rate).divide(TWELVE, 2, RoundingMode.HALF_UP);
        BigDecimal maturityAmount = maturityAmount(principal, rate, term, d.getPayout());
        BigDecimal totalInterest = maturityAmount.subtract(principal);

        LocalDate effectiveToday = today.isAfter(maturity) ? maturity : today;
        BigDecimal accrued = BigDecimal.ZERO;
        int fullMonths = 0;
        if (effectiveToday.isAfter(start)) {
            fullMonths = (int) ChronoUnit.MONTHS.between(start, effectiveToday);
            long extraDays = ChronoUnit.DAYS.between(start.plusMonths(fullMonths), effectiveToday);
            BigDecimal dayFactor = rate.multiply(BigDecimal.valueOf(extraDays)).divide(DAYS_PER_YEAR, MC);
            if (d.getPayout() == PayoutType.AT_MATURITY) {
                BigDecimal compounded = principal.multiply(onePlusMonthlyRate(rate).pow(fullMonths, MC), MC);
                accrued = compounded.multiply(BigDecimal.ONE.add(dayFactor), MC).subtract(principal);
            } else {
                accrued = monthlyInterest.multiply(BigDecimal.valueOf(fullMonths))
                        .add(principal.multiply(dayFactor, MC));
            }
        }
        accrued = accrued.setScale(2, RoundingMode.HALF_UP);

        BigDecimal early = fullMonths < 1 ? BigDecimal.ZERO
                : principal.multiply(rate).multiply(EARLY_TERMINATION_FACTOR)
                        .multiply(BigDecimal.valueOf(fullMonths)).divide(TWELVE, 2, RoundingMode.HALF_UP);

        long daysToMaturity = ChronoUnit.DAYS.between(today, maturity);
        Status status;
        if (d.isClosed()) {
            status = Status.CLOSED;
        } else if (daysToMaturity <= 0) {
            status = Status.MATURED;
        } else if (daysToMaturity <= MATURING_SOON_DAYS) {
            status = Status.MATURING_SOON;
        } else {
            status = Status.ACTIVE;
        }

        return new DepositView(d, cycle, start, maturity, principal, monthlyInterest, totalInterest,
                maturityAmount, accrued, early, daysToMaturity, status);
    }

    private static BigDecimal maturityAmount(BigDecimal principal, BigDecimal rate, int term, PayoutType payout) {
        if (payout == PayoutType.AT_MATURITY) {
            return principal.multiply(onePlusMonthlyRate(rate).pow(term, MC)).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal monthly = principal.multiply(rate).divide(TWELVE, 2, RoundingMode.HALF_UP);
        return principal.add(monthly.multiply(BigDecimal.valueOf(term)));
    }

    private static BigDecimal onePlusMonthlyRate(BigDecimal rate) {
        return BigDecimal.ONE.add(rate.divide(TWELVE, MC));
    }
}
