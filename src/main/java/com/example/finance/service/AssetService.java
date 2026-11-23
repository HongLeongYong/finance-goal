package com.example.finance.service;

import com.example.finance.form.AccountForm;
import com.example.finance.form.DepositForm;
import com.example.finance.model.Account;
import com.example.finance.model.TimeDeposit;
import com.example.finance.repository.AccountRepository;
import com.example.finance.repository.LedgerEntryRepository;
import com.example.finance.repository.TimeDepositRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 帳戶與定存的新增 / 修改 / 刪除。每次變動後更新本月淨資產快照。 */
@Service
public class AssetService {

    private final AccountRepository accountRepository;
    private final TimeDepositRepository depositRepository;
    private final LedgerEntryRepository entryRepository;
    private final NetWorthService netWorthService;

    public AssetService(AccountRepository accountRepository, TimeDepositRepository depositRepository,
            LedgerEntryRepository entryRepository, NetWorthService netWorthService) {
        this.accountRepository = accountRepository;
        this.depositRepository = depositRepository;
        this.entryRepository = entryRepository;
        this.netWorthService = netWorthService;
    }

    // ===== 帳戶 =====

    @Transactional(readOnly = true)
    public List<Account> activeAccounts() {
        return accountRepository.findByArchivedFalseOrderByTypeAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Account account(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> new NotFoundException("帳戶", id));
    }

    @Transactional
    public Account saveAccount(Long id, AccountForm form) {
        Account a = id == null ? new Account() : account(id);
        a.setName(form.getName().trim());
        a.setType(form.getType());
        a.setBalance(form.getBalance());
        a.setNote(blankToNull(form.getNote()));
        accountRepository.save(a);
        netWorthService.refreshCurrentMonth();
        return a;
    }

    /** 有記帳紀錄的帳戶只封存（保留歷史），沒有紀錄就直接刪除 */
    @Transactional
    public void removeAccount(Long id) {
        Account a = account(id);
        if (entryRepository.existsByAccountId(id)) {
            a.setArchived(true);
        } else {
            accountRepository.delete(a);
        }
        netWorthService.refreshCurrentMonth();
    }

    public static AccountForm toForm(Account a) {
        AccountForm f = new AccountForm();
        f.setName(a.getName());
        f.setType(a.getType());
        f.setBalance(a.getBalance());
        f.setNote(a.getNote());
        return f;
    }

    // ===== 定存 =====

    @Transactional(readOnly = true)
    public TimeDeposit deposit(Long id) {
        return depositRepository.findById(id).orElseThrow(() -> new NotFoundException("定存", id));
    }

    @Transactional(readOnly = true)
    public List<TimeDeposit> closedDeposits() {
        return depositRepository.findAllByOrderByClosedAscStartDateAsc().stream().filter(TimeDeposit::isClosed).toList();
    }

    @Transactional
    public TimeDeposit saveDeposit(Long id, DepositForm form) {
        TimeDeposit d = id == null ? new TimeDeposit() : deposit(id);
        d.setBank(form.getBank().trim());
        d.setPrincipal(form.getPrincipal());
        d.setAnnualRate(form.getAnnualRate());
        d.setStartDate(form.getStartDate());
        d.setTermMonths(form.getTermMonths());
        d.setRateType(form.getRateType());
        d.setPayout(form.getPayout());
        d.setMaturityAction(form.getMaturityAction());
        d.setNote(blankToNull(form.getNote()));
        depositRepository.save(d);
        netWorthService.refreshCurrentMonth();
        return d;
    }

    /** 解約 / 到期領回：標記為已結束，不再計入資產 */
    @Transactional
    public void closeDeposit(Long id) {
        deposit(id).setClosed(true);
        netWorthService.refreshCurrentMonth();
    }

    @Transactional
    public void deleteDeposit(Long id) {
        depositRepository.delete(deposit(id));
        netWorthService.refreshCurrentMonth();
    }

    public static DepositForm toForm(TimeDeposit d) {
        DepositForm f = new DepositForm();
        f.setBank(d.getBank());
        f.setPrincipal(d.getPrincipal());
        f.setAnnualRate(d.getAnnualRate());
        f.setStartDate(d.getStartDate());
        f.setTermMonths(d.getTermMonths());
        f.setRateType(d.getRateType());
        f.setPayout(d.getPayout());
        f.setMaturityAction(d.getMaturityAction());
        f.setNote(d.getNote());
        return f;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
