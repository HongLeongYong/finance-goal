package com.example.finance.repository;

import com.example.finance.model.NetWorthSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {

    Optional<NetWorthSnapshot> findBySnapshotMonth(LocalDate snapshotMonth);

    List<NetWorthSnapshot> findAllByOrderBySnapshotMonthAsc();
}
