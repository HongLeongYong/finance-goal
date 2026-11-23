package com.example.finance.repository;

import com.example.finance.model.StockTrade;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTradeRepository extends JpaRepository<StockTrade, Long> {

    /** 依時間順序（計算平均成本用） */
    List<StockTrade> findAllByOrderByTradeDateAscIdAsc();

    List<StockTrade> findAllByOrderByTradeDateDescIdDesc();

    boolean existsByAccountId(Long accountId);
}
