package com.example.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.finance.service.PayRate;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class PayRateTests {

    // 月薪 52,800、09:00–18:00 午休 12:00–13:00 → 每天 8 小時、每月 176 小時 → 時薪 300、分薪 5
    private final PayRate rate = new PayRate(new BigDecimal("52800"), 22,
            LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(12, 0), LocalTime.of(13, 0));

    @Test
    void ratesFromSalaryAndHours() {
        assertThat(rate.perHour()).isEqualByComparingTo("300");
        assertThat(rate.perMinute()).isEqualByComparingTo("5");
        assertThat(rate.perSecond()).isEqualByComparingTo("0.083333");
        assertThat(rate.workHoursPerDay()).isEqualByComparingTo("8");
    }

    @Test
    void describesAmountsAsWorkTime() {
        assertThat(rate.describe(new BigDecimal("2"))).isEqualTo("工作不到 1 分鐘");
        assertThat(rate.describe(new BigDecimal("150"))).isEqualTo("工作 30 分鐘");
        assertThat(rate.describe(new BigDecimal("300"))).isEqualTo("工作 1 小時");
        assertThat(rate.describe(new BigDecimal("625"))).isEqualTo("工作 2 小時 5 分");
        // 2 天以上用天數：9,600 = 32 小時 = 4 天
        assertThat(rate.describe(new BigDecimal("9600"))).isEqualTo("工作 4 天");
    }

    @Test
    void notConfiguredWithoutSalary() {
        PayRate empty = new PayRate(BigDecimal.ZERO, 22, LocalTime.of(9, 0), LocalTime.of(17, 0), null, null);
        assertThat(empty.isConfigured()).isFalse();
        assertThat(empty.describe(new BigDecimal("100"))).isEmpty();
        assertThat(empty.workMinutes(new BigDecimal("100"))).isEqualTo(-1);
    }

    @Test
    void lunchBreakIsExcludedFromWorkHours() {
        // 08:30–17:00、午休 12:15–13:30 → 8.5 − 1.25 = 7.25 小時；42,000 ÷ (22 × 7.25) = 263.32 / 小時
        PayRate mine = new PayRate(new BigDecimal("42000"), 22,
                LocalTime.of(8, 30), LocalTime.of(17, 0), LocalTime.of(12, 15), LocalTime.of(13, 30));
        assertThat(mine.workHoursPerDay()).isEqualByComparingTo("7.25");
        assertThat(mine.hoursPerMonth()).isEqualByComparingTo("159.5");
        assertThat(mine.perHour()).isEqualByComparingTo("263.322884");
        assertThat(mine.perMinute()).isEqualByComparingTo("4.388715");
    }
}
