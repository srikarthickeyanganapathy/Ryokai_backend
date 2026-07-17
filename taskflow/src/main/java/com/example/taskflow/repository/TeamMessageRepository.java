package com.example.taskflow.repository;

import com.example.taskflow.domain.TeamMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamMessageRepository extends JpaRepository<TeamMessage, Long> {
    List<TeamMessage> findByTeamIdOrderByCreatedAtAsc(Long teamId);
}
