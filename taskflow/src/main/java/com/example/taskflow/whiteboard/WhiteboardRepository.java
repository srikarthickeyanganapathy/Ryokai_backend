package com.example.taskflow.whiteboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WhiteboardRepository extends JpaRepository<Whiteboard, Long> {
    List<Whiteboard> findByCrewIdOrderByUpdatedAtDesc(Long crewId);
}
