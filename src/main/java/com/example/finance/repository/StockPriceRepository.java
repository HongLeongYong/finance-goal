package com.example.finance.repository;

import com.example.finance.model.StockPrice;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    Optional<StockPrice> findFirstByCodeOrderByPriceDateDesc(String code);

    Optional<StockPrice> findByCodeAndPriceDate(String code, LocalDate priceDate);

    Optional<StockPrice> findFirstByOrderByFetchedAtDesc();
}
