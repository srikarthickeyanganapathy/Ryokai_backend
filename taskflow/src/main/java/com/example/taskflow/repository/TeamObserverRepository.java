package com.example.taskflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.domain.TeamObserver;
import com.example.taskflow.domain.TeamObserverId;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Team;

import org.springframework.data.jpa.repository.EntityGraph;

public interface TeamObserverRepository extends JpaRepository<TeamObserver, TeamObserverId> {
    boolean existsByIdTeamIdAndIdUserId(Long teamId, Long userId);
    
    @EntityGraph(attributePaths = {"user"})
    List<TeamObserver> findByTeam(Team team);
    
    List<TeamObserver> findByUser(User user);
    Optional<TeamObserver> findByTeamAndUser(Team team, User user);
}
