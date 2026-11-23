package com.example.finance.repository;

import com.example.finance.model.TimeDeposit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeDepositRepository extends JpaRepository<TimeDeposit, Long> {

    List<TimeDeposit> findByClosedFalseOrderByStartDateAsc();

    List<TimeDeposit> findAllByOrderByClosedAscStartDateAsc();
}
