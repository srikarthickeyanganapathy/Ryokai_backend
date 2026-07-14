package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.CrewMember;
import com.example.taskflow.domain.CrewMemberId;

@Repository
public interface CrewMemberRepository extends JpaRepository<CrewMember, CrewMemberId> {

    List<CrewMember> findByIdCrewId(Long crewId);

    List<CrewMember> findByIdUserId(Long userId);

    boolean existsByIdCrewIdAndIdUserId(Long crewId, Long userId);
}
