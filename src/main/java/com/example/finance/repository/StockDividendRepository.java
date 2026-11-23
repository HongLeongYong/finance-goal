package com.example.finance.repository;

import com.example.finance.model.StockDividend;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDividendRepository extends JpaRepository<StockDividend, Long> {

    List<StockDividend> findAllByOrderByPayDateDescIdDesc();

    List<StockDividend> findByPayDateAfter(LocalDate date);
}
