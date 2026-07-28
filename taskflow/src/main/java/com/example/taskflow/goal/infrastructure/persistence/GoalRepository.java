package com.example.taskflow.goal.infrastructure.persistence;

import com.example.taskflow.goal.domain.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByOrganizationIdOrderByEndDateAsc(Long orgId);
}
