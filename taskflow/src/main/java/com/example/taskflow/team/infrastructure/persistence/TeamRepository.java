package com.example.taskflow.team.infrastructure.persistence;

import com.example.taskflow.team.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByOrganizationId(Long orgId);
}
