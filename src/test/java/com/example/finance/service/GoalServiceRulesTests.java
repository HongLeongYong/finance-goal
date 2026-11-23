package com.example.finance.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GoalServiceRulesTests {

    @Test
    void oneOffCategoriesAreNotSuggestedForCutting() {
        // 近 3 個月只有 1 個月花過（例如 10 月去花蓮）→ 一次性支出
        assertThat(GoalService.isRecurring(1, 3)).isFalse();
        // 有 2 個月以上都花過 → 固定支出，可以建議少花
        assertThat(GoalService.isRecurring(2, 3)).isTrue();
        assertThat(GoalService.isRecurring(3, 3)).isTrue();
        // 只有 1 個月的資料，無法判斷 → 全部都算
        assertThat(GoalService.isRecurring(1, 1)).isTrue();
    }
}
