package com.example.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.model.MaturityAction;
import com.example.finance.model.PayoutType;
import com.example.finance.model.TimeDeposit;
import com.example.finance.service.DepositCalculator;
import com.example.finance.service.DepositCalculator.DepositView;
import com.example.finance.service.DepositCalculator.Status;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DepositCalculatorTests {

    private static TimeDeposit deposit(String principal, String rate, LocalDate start, int months,
            PayoutType payout, MaturityAction action) {
        TimeDeposit d = new TimeDeposit();
        d.setBank("測試銀行");
        d.setPrincipal(new BigDecimal(principal));
        d.setAnnualRate(new BigDecimal(rate));
        d.setStartDate(start);
        d.setTermMonths(months);
        d.setPayout(payout);
        d.setMaturityAction(action);
        return d;
    }

    @Test
    void monthlyPayoutIsSimpleInterest() {
        // 120 萬、年利率 1.5%、12 個月、每月領息 → 每月 1,500，一年 18,000
        TimeDeposit d = deposit("1200000", "1.5", LocalDate.of(2026, 1, 1), 12, PayoutType.MONTHLY, MaturityAction.CLOSE);
        DepositView v = DepositCalculator.calculate(d, LocalDate.of(2026, 4, 1));
        assertThat(v.monthlyInterest()).isEqualByComparingTo("1500");
        assertThat(v.totalInterest()).isEqualByComparingTo("18000");
        assertThat(v.maturityDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(v.accruedInterest()).isEqualByComparingTo("4500");   // 滿 3 個月
        assertThat(v.earlyTerminationInterest()).isEqualByComparingTo("3600"); // 4500 × 0.8
        assertThat(v.status()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void atMaturityCompoundsMonthly() {
        // 100 萬、1.2%、12 個月整存整付：1,000,000 × (1.001)^12 = 1,012,066.22
        TimeDeposit d = deposit("1000000", "1.2", LocalDate.of(2026, 1, 1), 12, PayoutType.AT_MATURITY, MaturityAction.CLOSE);
        DepositView v = DepositCalculator.calculate(d, LocalDate.of(2026, 1, 1));
        assertThat(v.maturityAmount()).isEqualByComparingTo("1012066.22");
        assertThat(v.accruedInterest()).isEqualByComparingTo("0");
        assertThat(v.earlyTerminationInterest()).isEqualByComparingTo("0"); // 未滿 1 個月不計息
    }

    @Test
    void maturingSoonAndMaturedStatus() {
        TimeDeposit d = deposit("100000", "1.7", LocalDate.of(2026, 1, 10), 6, PayoutType.AT_MATURITY, MaturityAction.CLOSE);
        assertThat(DepositCalculator.calculate(d, LocalDate.of(2026, 6, 20)).status()).isEqualTo(Status.MATURING_SOON);
        DepositView matured = DepositCalculator.calculate(d, LocalDate.of(2026, 8, 1));
        assertThat(matured.status()).isEqualTo(Status.MATURED);
        // 到期待解約的整存整付，資產以到期本息計
        assertThat(matured.assetValue()).isEqualByComparingTo(matured.maturityAmount());
    }

    @Test
    void autoRenewRollsIntoCurrentCycleWithInterestAdded() {
        TimeDeposit d = deposit("1000000", "1.2", LocalDate.of(2025, 1, 1), 12, PayoutType.AT_MATURITY, MaturityAction.AUTO_RENEW);
        DepositView v = DepositCalculator.calculate(d, LocalDate.of(2026, 3, 1));
        assertThat(v.cycle()).isEqualTo(2);
        assertThat(v.cycleStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(v.cyclePrincipal()).isEqualByComparingTo("1012066.22");
        assertThat(v.status()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void principalRenewKeepsPrincipal() {
        TimeDeposit d = deposit("1000000", "1.2", LocalDate.of(2025, 1, 1), 12, PayoutType.AT_MATURITY, MaturityAction.PRINCIPAL_RENEW);
        DepositView v = DepositCalculator.calculate(d, LocalDate.of(2026, 3, 1));
        assertThat(v.cycle()).isEqualTo(2);
        assertThat(v.cyclePrincipal()).isEqualByComparingTo("1000000");
    }
}
