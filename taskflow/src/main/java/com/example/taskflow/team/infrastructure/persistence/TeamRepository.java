package com.example.taskflow.team.infrastructure.persistence;

import com.example.taskflow.team.domain.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @EntityGraph(attributePaths = {"organization","createdBy"})
    @Override
    java.util.Optional<Team> findById(Long id);

    List<Team> findByOrganizationId(Long orgId);
}
