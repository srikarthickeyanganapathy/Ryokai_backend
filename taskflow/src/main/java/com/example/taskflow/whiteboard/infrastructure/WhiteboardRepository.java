package com.example.taskflow.whiteboard.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.whiteboard.domain.Whiteboard;

import java.util.List;

public interface WhiteboardRepository extends JpaRepository<Whiteboard, Long> {
    List<Whiteboard> findByCrewIdOrderByUpdatedAtDesc(Long crewId);
}
