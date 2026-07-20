package com.example.taskflow.repository;

import com.example.taskflow.domain.Whiteboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WhiteboardRepository extends JpaRepository<Whiteboard, Long> {
    List<Whiteboard> findByCrewIdOrderByUpdatedAtDesc(Long crewId);
}
