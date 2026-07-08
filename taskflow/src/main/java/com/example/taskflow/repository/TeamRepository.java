package com.example.taskflow.repository;

import com.example.taskflow.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByOrganizationId(Long orgId);
    List<Team> findByMembersId(Long userId);
}
