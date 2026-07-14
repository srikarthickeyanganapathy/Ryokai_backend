package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.CrewMessage;

@Repository
public interface CrewMessageRepository extends JpaRepository<CrewMessage, Long> {

    List<CrewMessage> findByChannel_IdOrderByCreatedAtAsc(Long channelId);
}
