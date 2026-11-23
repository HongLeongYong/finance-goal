package com.example.finance.service;

import com.example.finance.model.Account;
import com.example.finance.model.NetWorthSnapshot;
import com.example.finance.repository.AccountRepository;
import com.example.finance.repository.NetWorthSnapshotRepository;
import com.example.finance.repository.TimeDepositRepository;
import com.example.finance.service.DepositCalculator.DepositView;
import com.example.finance.stock.PortfolioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 淨資產 = 現金 + 銀行活存 + 定存 + 股票市值 − 負債 */
@Service
public class NetWorthService {

    private final AccountRepository accountRepository;
    private final TimeDepositRepository depositRepository;
    private final NetWorthSnapshotRepository snapshotRepository;
    private final PortfolioService portfolioService;
    private final Clock clock;

    public NetWorthService(AccountRepository accountRepository, TimeDepositRepository depositRepository,
            NetWorthSnapshotRepository snapshotRepository, PortfolioService portfolioService, Clock clock) {
        this.accountRepository = accountRepository;
        this.depositRepository = depositRepository;
        this.snapshotRepository = snapshotRepository;
        this.portfolioService = portfolioService;
        this.clock = clock;
    }

    public record AssetSummary(
            BigDecimal cash,
            BigDecimal bank,
            BigDecimal deposits,
            BigDecimal stocks,
            BigDecimal liabilities,
            BigDecimal depositMonthlyInterest,
            BigDecimal dividendMonthly,        // 近 12 個月股利 ÷ 12
            List<DepositView> maturingSoon) {

        public BigDecimal totalAssets() {
            return cash.add(bank).add(deposits).add(stocks);
        }

        /** 每月被動收入 = 定存利息 + 近 12 個月平均股利 */
        public BigDecimal monthlyPassiveIncome() {
            return depositMonthlyInterest.add(dividendMonthly);
        }

        public BigDecimal netWorth() {
            return totalAssets().subtract(liabilities);
        }

        /** 你的錢每分鐘幫你賺多少：每月被動收入 ÷ 一個月平均分鐘數（30.4375 天 × 1440） */
        public BigDecimal passivePerMinute() {
            return monthlyPassiveIncome().divide(MINUTES_PER_MONTH, 4, java.math.RoundingMode.HALF_UP);
        }

        public boolean hasAnyAsset() {
            return totalAssets().signum() != 0 || liabilities.signum() != 0;
        }
    }

    private static final BigDecimal MINUTES_PER_MONTH = new BigDecimal("43830");

    @Transactional(readOnly = true)
    public List<DepositView> activeDeposits() {
        LocalDate today = LocalDate.now(clock);
        return depositRepository.findByClosedFalseOrderByStartDateAsc().stream()
                .map(d -> DepositCalculator.calculate(d, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetSummary summary() {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal bank = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        for (Account a : accountRepository.findByArchivedFalseOrderByTypeAscIdAsc()) {
            switch (a.getType()) {
                case CASH -> cash = cash.add(a.getBalance());
                case BANK -> bank = bank.add(a.getBalance());
                case LIABILITY -> liabilities = liabilities.add(a.getBalance());
            }
        }
        List<DepositView> deposits = activeDeposits();
        BigDecimal depositTotal = deposits.stream().map(DepositView::assetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthlyInterest = deposits.stream()
                .filter(v -> v.status() != DepositCalculator.Status.MATURED)
                .map(DepositView::monthlyInterest).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DepositView> maturingSoon = deposits.stream()
                .filter(v -> v.status() == DepositCalculator.Status.MATURING_SOON
                        || v.status() == DepositCalculator.Status.MATURED)
                .toList();
        PortfolioService.Portfolio portfolio = portfolioService.portfolio();
        BigDecimal dividendMonthly = portfolio.dividends12m().divide(BigDecimal.valueOf(12), 0, java.math.RoundingMode.HALF_UP);
        return new AssetSummary(cash, bank, depositTotal, portfolio.marketValue(), liabilities, monthlyInterest,
                dividendMonthly, maturingSoon);
    }

    /** 更新（或建立）本月快照。每次資產變動和每天 23:55 都會呼叫。 */
    @Transactional
    public void refreshCurrentMonth() {
        AssetSummary s = summary();
        LocalDate month = YearMonth.now(clock).atDay(1);
        NetWorthSnapshot snap = snapshotRepository.findBySnapshotMonth(month).orElseGet(NetWorthSnapshot::new);
        snap.setSnapshotMonth(month);
        snap.setTotalAssets(s.totalAssets());
        snap.setTotalLiabilities(s.liabilities());
        snap.setNetWorth(s.netWorth());
        snap.setUpdatedAt(LocalDateTime.now(clock));
        snapshotRepository.save(snap);
    }

    @Scheduled(cron = "${finance.snapshot.cron}", zone = "Asia/Taipei")
    public void scheduledSnapshot() {
        refreshCurrentMonth();
    }

    @Transactional(readOnly = true)
    public List<NetWorthSnapshot> history() {
        return snapshotRepository.findAllByOrderBySnapshotMonthAsc();
    }
}
