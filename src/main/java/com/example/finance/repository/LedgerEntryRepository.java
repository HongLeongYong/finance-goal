package com.example.finance.repository;

import com.example.finance.model.EntryType;
import com.example.finance.model.LedgerEntry;
import com.example.finance.service.CategoryTotal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByEntryDateBetweenOrderByEntryDateDescIdDesc(LocalDate from, LocalDate to);

    List<LedgerEntry> findTop10ByOrderByEntryDateDescIdDesc();

    boolean existsByCategoryId(Long categoryId);

    boolean existsByAccountId(Long accountId);

    @Query("""
            select coalesce(sum(e.amount), 0) from LedgerEntry e
            where e.type = :type and e.entryDate between :from and :to
            """)
    BigDecimal sumByType(@Param("type") EntryType type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select new com.example.finance.service.CategoryTotal(c.id, c.name, c.icon, sum(e.amount))
            from LedgerEntry e join e.category c
            where e.type = :type and e.entryDate between :from and :to
            group by c.id, c.name, c.icon
            order by sum(e.amount) desc
            """)
    List<CategoryTotal> sumByCategory(@Param("type") EntryType type, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
