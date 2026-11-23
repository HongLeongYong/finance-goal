package com.example.finance.repository;

import com.example.finance.model.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByArchivedFalseOrderByTypeAscIdAsc();
}
