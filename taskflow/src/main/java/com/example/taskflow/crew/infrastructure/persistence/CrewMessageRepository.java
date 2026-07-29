package com.example.taskflow.crew.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.crew.domain.CrewMessage;

public interface CrewMessageRepository extends JpaRepository<CrewMessage, Long> {

    List<CrewMessage> findByChannel_IdOrderByCreatedAtAsc(Long channelId);
}
