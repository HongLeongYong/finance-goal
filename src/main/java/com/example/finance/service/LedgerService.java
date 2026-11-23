package com.example.finance.service;

import com.example.finance.form.CategoryForm;
import com.example.finance.form.EntryForm;
import com.example.finance.model.Account;
import com.example.finance.model.Category;
import com.example.finance.model.EntryType;
import com.example.finance.model.LedgerEntry;
import com.example.finance.repository.AccountRepository;
import com.example.finance.repository.CategoryRepository;
import com.example.finance.repository.LedgerEntryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 記帳：新增 / 修改 / 刪除收支（同步調整帳戶餘額），以及月報表。 */
@Service
public class LedgerService {

    private final LedgerEntryRepository entryRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final NetWorthService netWorthService;

    public LedgerService(LedgerEntryRepository entryRepository, CategoryRepository categoryRepository,
            AccountRepository accountRepository, NetWorthService netWorthService) {
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.netWorthService = netWorthService;
    }

    // ===== 收支 =====

    /** 列表篩選條件：type / categoryId / accountId 為 null 表示不篩選 */
    public record Filter(EntryType type, Long categoryId, Long accountId) {
        boolean matches(LedgerEntry e) {
            return (type == null || e.getType() == type)
                    && (categoryId == null || e.getCategory().getId().equals(categoryId))
                    && (accountId == null || (e.getAccount() != null && e.getAccount().getId().equals(accountId)));
        }
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entries(YearMonth month, Filter filter) {
        return entryRepository.findByEntryDateBetweenOrderByEntryDateDescIdDesc(month.atDay(1), month.atEndOfMonth())
                .stream().filter(filter::matches).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> recent() {
        return entryRepository.findTop10ByOrderByEntryDateDescIdDesc();
    }

    @Transactional(readOnly = true)
    public LedgerEntry entry(Long id) {
        return entryRepository.findById(id).orElseThrow(() -> new NotFoundException("記帳紀錄", id));
    }

    @Transactional
    public LedgerEntry saveEntry(Long id, EntryForm form) {
        LedgerEntry e;
        if (id == null) {
            e = new LedgerEntry();
        } else {
            e = entry(id);
            // 先把舊的金額從帳戶退回，再套用新的
            if (e.getAccount() != null) {
                e.getAccount().revert(e.getType(), e.getAmount());
            }
        }
        Category category = category(form.getCategoryId());
        if (category.getType() != form.getType()) {
            throw new IllegalArgumentException("分類「" + category.getName() + "」不是" + form.getType().getLabel() + "分類");
        }
        Account account = form.getAccountId() == null ? null
                : accountRepository.findById(form.getAccountId())
                        .orElseThrow(() -> new NotFoundException("帳戶", form.getAccountId()));

        e.setEntryDate(form.getEntryDate());
        e.setType(form.getType());
        e.setAmount(form.getAmount());
        e.setCategory(category);
        e.setAccount(account);
        e.setNote(AssetService.blankToNull(form.getNote()));
        if (account != null) {
            account.apply(e.getType(), e.getAmount());
        }
        entryRepository.save(e);
        netWorthService.refreshCurrentMonth();
        return e;
    }

    @Transactional
    public void deleteEntry(Long id) {
        LedgerEntry e = entry(id);
        if (e.getAccount() != null) {
            e.getAccount().revert(e.getType(), e.getAmount());
        }
        entryRepository.delete(e);
        netWorthService.refreshCurrentMonth();
    }

    public static EntryForm toForm(LedgerEntry e) {
        EntryForm f = new EntryForm();
        f.setEntryDate(e.getEntryDate());
        f.setType(e.getType());
        f.setAmount(e.getAmount());
        f.setCategoryId(e.getCategory().getId());
        f.setAccountId(e.getAccount() == null ? null : e.getAccount().getId());
        f.setNote(e.getNote());
        return f;
    }

    // ===== 分類 =====

    @Transactional(readOnly = true)
    public List<Category> categories(EntryType type) {
        return categoryRepository.findByTypeOrderBySortOrderAscIdAsc(type);
    }

    @Transactional(readOnly = true)
    public List<Category> allCategories() {
        return categoryRepository.findAllByOrderByTypeAscSortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Category category(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("分類", id));
    }

    @Transactional
    public void addCategory(CategoryForm form) {
        String name = form.getName().trim();
        if (categoryRepository.existsByTypeAndName(form.getType(), name)) {
            throw new IllegalArgumentException("已經有「" + name + "」這個分類了");
        }
        Category c = new Category();
        c.setName(name);
        c.setType(form.getType());
        c.setIcon(form.getIcon() == null ? "" : form.getIcon().trim());
        c.setSortOrder(500);
        categoryRepository.save(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = category(id);
        if (entryRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException("「" + c.getName() + "」已經有記帳紀錄，不能刪除");
        }
        categoryRepository.delete(c);
    }

    // ===== 月報 =====

    public record CategoryRow(String name, String icon, BigDecimal amount, BigDecimal share,
            BigDecimal lastMonthAmount) {

        /** 和上個月比的變化率；上個月沒花過回傳 null */
        public BigDecimal changeRate() {
            if (lastMonthAmount.signum() == 0) {
                return null;
            }
            return amount.subtract(lastMonthAmount).divide(lastMonthAmount, 4, RoundingMode.HALF_UP);
        }

        public String displayName() {
            return icon == null || icon.isBlank() ? name : icon + " " + name;
        }
    }

    public record MonthReport(YearMonth month, BigDecimal income, BigDecimal expense,
            BigDecimal lastMonthIncome, BigDecimal lastMonthExpense,
            List<CategoryRow> expenseRows, List<CategoryRow> incomeRows) {

        public BigDecimal net() {
            return income.subtract(expense);
        }

        /** 儲蓄率 =（收入 − 支出）÷ 收入；沒有收入回傳 null */
        public BigDecimal savingsRate() {
            return income.signum() == 0 ? null : net().divide(income, 4, RoundingMode.HALF_UP);
        }
    }

    @Transactional(readOnly = true)
    public MonthReport report(YearMonth month) {
        YearMonth prev = month.minusMonths(1);
        BigDecimal income = sum(EntryType.INCOME, month);
        BigDecimal expense = sum(EntryType.EXPENSE, month);
        return new MonthReport(month, income, expense,
                sum(EntryType.INCOME, prev), sum(EntryType.EXPENSE, prev),
                rows(EntryType.EXPENSE, month, prev, expense),
                rows(EntryType.INCOME, month, prev, income));
    }

    private BigDecimal sum(EntryType type, YearMonth m) {
        return entryRepository.sumByType(type, m.atDay(1), m.atEndOfMonth());
    }

    private List<CategoryRow> rows(EntryType type, YearMonth month, YearMonth prev, BigDecimal total) {
        Map<Long, BigDecimal> last = entryRepository.sumByCategory(type, prev.atDay(1), prev.atEndOfMonth()).stream()
                .collect(Collectors.toMap(CategoryTotal::categoryId, CategoryTotal::amount));
        Function<BigDecimal, BigDecimal> share = amt -> total.signum() == 0 ? BigDecimal.ZERO
                : amt.divide(total, 4, RoundingMode.HALF_UP);
        return entryRepository.sumByCategory(type, month.atDay(1), month.atEndOfMonth()).stream()
                .map(t -> new CategoryRow(t.name(), t.icon(), t.amount(), share.apply(t.amount()),
                        last.getOrDefault(t.categoryId(), BigDecimal.ZERO)))
                .toList();
    }
}
