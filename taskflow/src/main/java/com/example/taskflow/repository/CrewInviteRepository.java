package com.example.taskflow.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.CrewInvite;

@Repository
public interface CrewInviteRepository extends JpaRepository<CrewInvite, UUID> {

    List<CrewInvite> findByCrew_Id(Long crewId);

    List<CrewInvite> findByEmail(String email);
}
