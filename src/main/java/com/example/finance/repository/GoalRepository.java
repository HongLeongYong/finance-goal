package com.example.finance.repository;

import com.example.finance.model.Goal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findAllByOrderByIdAsc();
}
