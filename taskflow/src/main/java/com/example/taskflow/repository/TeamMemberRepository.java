package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.TeamMember;
import com.example.taskflow.domain.TeamMemberId;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMemberId> {

    List<TeamMember> findByIdTeamId(Long teamId);

    List<TeamMember> findByIdUserId(Long userId);

    boolean existsByIdTeamIdAndIdUserId(Long teamId, Long userId);
}
