package com.example.finance.repository;

import com.example.finance.model.GoalContribution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    List<GoalContribution> findByGoalIdOrderByContributionDateAscIdAsc(Long goalId);

    List<GoalContribution> findByGoalIdOrderByContributionDateDescIdDesc(Long goalId);
}
