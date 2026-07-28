package com.example.taskflow.crew.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.crew.domain.CrewMessage;

@Repository
public interface CrewMessageRepository extends JpaRepository<CrewMessage, Long> {

    List<CrewMessage> findByChannel_IdOrderByCreatedAtAsc(Long channelId);
}
