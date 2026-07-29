package com.example.taskflow.crew.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.crew.domain.CrewChannel;

public interface CrewChannelRepository extends JpaRepository<CrewChannel, Long> {

    List<CrewChannel> findByCrew_IdOrderByPositionAsc(Long crewId);
}
