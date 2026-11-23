package com.example.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.finance.model.Account;
import com.example.finance.model.Category;
import com.example.finance.model.EntryType;
import com.example.finance.repository.AccountRepository;
import com.example.finance.repository.CategoryRepository;
import com.example.finance.repository.GoalRepository;
import com.example.finance.repository.LedgerEntryRepository;
import com.example.finance.repository.NetWorthSnapshotRepository;
import com.example.finance.repository.TimeDepositRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 用 MockMvc 走過每一頁和主要流程（H2 記憶體資料庫，每個測試結束自動 rollback） */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class WebFlowTests {

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired LedgerEntryRepository entries;
    @Autowired TimeDepositRepository deposits;
    @Autowired NetWorthSnapshotRepository snapshots;
    @Autowired GoalRepository goals;

    private Category firstCategory(EntryType type) {
        return categories.findByTypeOrderBySortOrderAscIdAsc(type).getFirst();
    }

    @Test
    void allPagesRender() throws Exception {
        for (String url : new String[] {"/", "/ledger", "/ledger/report", "/assets", "/settings", "/goals", "/ledger?month=2025-01"}) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    void settingSalaryShowsPerMinute() throws Exception {
        mvc.perform(post("/settings/profile")
                        .param("monthlySalary", "52800").param("workDaysPerMonth", "22")
                        .param("workStartTime", "09:00").param("workEndTime", "18:00")
                        .param("breakStartTime", "12:00").param("breakEndTime", "13:00"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("$5.00")))   // 分薪
                .andExpect(content().string(containsString("earn-today")))
                .andExpect(content().string(containsString("data-break-start=\"12:00\"")));
    }

    @Test
    void rejectsLunchBreakOutsideWorkHours() throws Exception {
        mvc.perform(post("/settings/profile")
                        .param("monthlySalary", "42000").param("workDaysPerMonth", "22")
                        .param("workStartTime", "08:30").param("workEndTime", "17:00")
                        .param("breakStartTime", "18:00").param("breakEndTime", "19:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("午休要同時填開始和結束")));
    }

    @Test
    void entriesAdjustAccountBalanceAndCanBeEditedAndDeleted() throws Exception {
        mvc.perform(post("/assets/accounts").param("name", "測試活存").param("type", "BANK").param("balance", "10000"))
                .andExpect(status().is3xxRedirection());
        Account acc = accounts.findByArchivedFalseOrderByTypeAscIdAsc().getFirst();
        Long food = firstCategory(EntryType.EXPENSE).getId();
        String today = LocalDate.now().toString();

        mvc.perform(post("/ledger").param("entryDate", today).param("type", "EXPENSE").param("amount", "250")
                        .param("categoryId", food.toString()).param("accountId", acc.getId().toString()).param("note", "午餐"))
                .andExpect(status().is3xxRedirection());
        assertThat(accounts.findById(acc.getId()).orElseThrow().getBalance()).isEqualByComparingTo("9750");

        Long entryId = entries.findAll().getFirst().getId();
        mvc.perform(post("/ledger/" + entryId).param("entryDate", today).param("type", "EXPENSE").param("amount", "100")
                        .param("categoryId", food.toString()).param("accountId", acc.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(accounts.findById(acc.getId()).orElseThrow().getBalance()).isEqualByComparingTo("9900");

        mvc.perform(get("/ledger")).andExpect(content().string(containsString("−$100")));
        mvc.perform(get("/ledger/report")).andExpect(status().isOk());

        mvc.perform(post("/ledger/" + entryId + "/delete")).andExpect(status().is3xxRedirection());
        assertThat(accounts.findById(acc.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10000");
        assertThat(snapshots.findAll()).isNotEmpty();
    }

    @Test
    void creditCardExpenseIncreasesDebt() throws Exception {
        mvc.perform(post("/assets/accounts").param("name", "信用卡").param("type", "LIABILITY").param("balance", "0"));
        Account card = accounts.findByArchivedFalseOrderByTypeAscIdAsc().getFirst();
        mvc.perform(post("/ledger").param("entryDate", LocalDate.now().toString()).param("type", "EXPENSE")
                .param("amount", "3000").param("categoryId", firstCategory(EntryType.EXPENSE).getId().toString())
                .param("accountId", card.getId().toString()));
        assertThat(accounts.findById(card.getId()).orElseThrow().getBalance()).isEqualByComparingTo("3000");
    }

    @Test
    void rejectsCategoryOfWrongType() throws Exception {
        mvc.perform(post("/ledger").param("entryDate", LocalDate.now().toString()).param("type", "EXPENSE")
                .param("amount", "100").param("categoryId", firstCategory(EntryType.INCOME).getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(entries.count()).isZero();
    }

    @Test
    void depositIsCountedInNetWorth() throws Exception {
        mvc.perform(post("/assets/deposits").param("bank", "台灣銀行").param("principal", "500000")
                        .param("annualRate", "1.7").param("startDate", LocalDate.now().toString())
                        .param("termMonths", "12").param("rateType", "FIXED").param("payout", "MONTHLY")
                        .param("maturityAction", "AUTO_RENEW"))
                .andExpect(status().is3xxRedirection());
        assertThat(deposits.count()).isEqualTo(1);
        mvc.perform(get("/assets"))
                .andExpect(content().string(containsString("台灣銀行")))
                .andExpect(content().string(containsString("$708")))   // 500,000 × 1.7% ÷ 12 = 708.33
                .andExpect(content().string(containsString("$500,000")));

        Long id = deposits.findAll().getFirst().getId();
        mvc.perform(post("/assets/deposits/" + id + "/close")).andExpect(status().is3xxRedirection());
        assertThat(deposits.findById(id).orElseThrow().isClosed()).isTrue();
    }

    @Test
    void cannotDeleteCategoryInUse() throws Exception {
        Category food = firstCategory(EntryType.EXPENSE);
        mvc.perform(post("/ledger").param("entryDate", LocalDate.now().toString()).param("type", "EXPENSE")
                .param("amount", "80").param("categoryId", food.getId().toString()));
        mvc.perform(post("/settings/categories/" + food.getId() + "/delete")).andExpect(status().is3xxRedirection());
        assertThat(categories.existsById(food.getId())).isTrue();

        mvc.perform(post("/settings/categories").param("type", "EXPENSE").param("icon", "🐶").param("name", "寵物"))
                .andExpect(status().is3xxRedirection());
        assertThat(categories.existsByTypeAndName(EntryType.EXPENSE, "寵物")).isTrue();
    }

    @Test
    void goalsInAllThreeModes() throws Exception {
        // 先放一點資料：活存 100 萬、本月結餘 2 萬
        mvc.perform(post("/assets/accounts").param("name", "活存").param("type", "BANK").param("balance", "1000000"));
        mvc.perform(post("/ledger").param("entryDate", LocalDate.now().toString()).param("type", "INCOME")
                .param("amount", "50000").param("categoryId", firstCategory(EntryType.INCOME).getId().toString()));
        mvc.perform(post("/ledger").param("entryDate", LocalDate.now().toString()).param("type", "EXPENSE")
                .param("amount", "30000").param("categoryId", firstCategory(EntryType.EXPENSE).getId().toString()));

        // 模式三：被動收入每月 3 萬 → 目標 900 萬，10 年，年報酬 5%
        mvc.perform(post("/goals").param("name", "財務自由").param("targetType", "PASSIVE_INCOME")
                        .param("monthlyPassiveIncome", "30000").param("withdrawalRate", "4")
                        .param("mode", "PLAN").param("years", "10").param("annualReturnOverride", "5"))
                .andExpect(status().is3xxRedirection());
        Long planId = goals.findAll().getFirst().getId();
        mvc.perform(get("/goals/" + planId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$9,000,000")))
                .andExpect(content().string(containsString("每月要存")))
                .andExpect(content().string(containsString("提高報酬")))
                .andExpect(content().string(containsString("一次投入")));

        // 模式一：不填目標也可以
        mvc.perform(post("/goals").param("name", "十年後").param("targetType", "AMOUNT")
                        .param("withdrawalRate", "4").param("mode", "PROJECT").param("years", "10"))
                .andExpect(status().is3xxRedirection());
        // 模式二
        mvc.perform(post("/goals").param("name", "買車").param("targetType", "AMOUNT").param("targetAmount", "1500000")
                        .param("withdrawalRate", "4").param("mode", "TIME_TO_TARGET"))
                .andExpect(status().is3xxRedirection());
        assertThat(goals.count()).isEqualTo(3);
        for (var g : goals.findAll()) {
            mvc.perform(get("/goals/" + g.getId())).andExpect(status().isOk());
        }
        mvc.perform(get("/goals")).andExpect(content().string(containsString("10 年後約有")));
        mvc.perform(get("/")).andExpect(content().string(containsString("目標進度")));
    }

    @Test
    void planModeRequiresYears() throws Exception {
        mvc.perform(post("/goals").param("name", "沒填年數").param("targetType", "AMOUNT").param("targetAmount", "1000000")
                        .param("withdrawalRate", "4").param("mode", "PLAN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("這個模式要填年數")));
        assertThat(goals.count()).isZero();
    }

    @Test
    void contributionGoalAccumulatesWithoutTouchingLedger() throws Exception {
        mvc.perform(post("/goals").param("name", "日本旅遊").param("targetType", "AMOUNT").param("targetAmount", "60000")
                        .param("withdrawalRate", "4").param("mode", "PLAN").param("years", "1"))
                .andExpect(status().is3xxRedirection());
        Long id = goals.findAll().getFirst().getId();
        long entriesBefore = entries.count();

        String today = LocalDate.now().toString();
        mvc.perform(post("/goals/" + id + "/contributions").param("deposit", "true")
                        .param("contributionDate", today).param("amount", "5000").param("note", "第一筆"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/goals/" + id + "/contributions").param("deposit", "true")
                .param("contributionDate", today).param("amount", "3000"));
        // 存入後自動改成累積式
        assertThat(goals.findById(id).orElseThrow().getPrincipalMode().name()).isEqualTo("CONTRIBUTIONS");

        // 不能提出超過已存的錢
        mvc.perform(post("/goals/" + id + "/contributions").param("deposit", "false")
                .param("contributionDate", today).param("amount", "9000"));
        mvc.perform(post("/goals/" + id + "/contributions").param("deposit", "false")
                .param("contributionDate", today).param("amount", "1000"));

        // 本金 = 5,000 + 3,000 − 1,000 = 7,000；只是標記，不會新增記帳
        mvc.perform(get("/goals/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$7,000")))
                .andExpect(content().string(containsString("存入紀錄累積")))
                .andExpect(content().string(containsString("本月已存")));
        assertThat(entries.count()).isEqualTo(entriesBefore);
        mvc.perform(get("/goals")).andExpect(content().string(containsString("累積式・本月已存 $7,000")));
    }

    @Test
    void manualPrincipalRequiresAmount() throws Exception {
        mvc.perform(post("/goals").param("name", "手動本金").param("targetType", "AMOUNT").param("targetAmount", "100000")
                        .param("withdrawalRate", "4").param("mode", "TIME_TO_TARGET").param("principalMode", "MANUAL"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("選了「手動輸入」就要填本金")));
    }
}
