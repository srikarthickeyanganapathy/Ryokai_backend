package com.example.taskflow.team.infrastructure.persistence;

import com.example.taskflow.team.domain.TeamMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {
    List<TeamMessage> findByTeamIdOrderByCreatedAtAsc(Long teamId);
}
