package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.CrewChannel;

@Repository
public interface CrewChannelRepository extends JpaRepository<CrewChannel, Long> {

    List<CrewChannel> findByCrew_IdOrderByPositionAsc(Long crewId);
}
