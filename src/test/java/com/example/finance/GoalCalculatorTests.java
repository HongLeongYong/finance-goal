package com.example.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.finance.service.GoalCalculator;
import org.junit.jupiter.api.Test;

/** 規格裡的例子：本金 100 萬、每月存 2 萬、年報酬 5% */
class GoalCalculatorTests {

    static final double PV = 1_000_000, PMT = 20_000, R = 0.05;

    @Test
    void projectTenYears() {
        assertThat(GoalCalculator.futureValue(PV, PMT, R, 120)).isCloseTo(4_752_655, within(1_000.0));
    }

    @Test
    void monthsToTenMillion() {
        // 約 18 年 9~10 個月
        assertThat(GoalCalculator.monthsToTarget(PV, PMT, R, 10_000_000)).isBetween(225, 226);
    }

    @Test
    void planTenMillionInTenYears() {
        assertThat(GoalCalculator.requiredMonthly(PV, R, 120, 10_000_000)).isCloseTo(53_792, within(50.0));
        assertThat(GoalCalculator.requiredAnnualReturn(PV, PMT, 120, 10_000_000)).isBetween(0.14, 0.16);
        assertThat(GoalCalculator.requiredPrincipal(PMT, R, 120, 10_000_000) - PV).isCloseTo(3_186_000, within(2_000.0));
    }

    @Test
    void edgeCases() {
        assertThat(GoalCalculator.monthsToTarget(0, 1000, 0, 12_000)).isEqualTo(12);
        assertThat(GoalCalculator.monthsToTarget(5000, 0, 0, 1000)).isZero();          // 已達成
        assertThat(GoalCalculator.monthsToTarget(0, -500, 0.05, 1_000_000)).isNull();  // 每月虧錢，永遠達不到
        assertThat(GoalCalculator.requiredMonthly(20_000_000, R, 120, 10_000_000)).isZero();
        // 通膨 2%、名目 5% → 實質約 2.94%
        assertThat(GoalCalculator.realRate(0.05, 0.02)).isCloseTo(0.02941, within(0.0001));
    }
}
