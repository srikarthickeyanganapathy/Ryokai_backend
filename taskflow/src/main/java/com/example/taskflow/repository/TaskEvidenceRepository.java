package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.TaskEvidence;

@Repository
public interface TaskEvidenceRepository extends JpaRepository<TaskEvidence, Long> {

    List<TaskEvidence> findByTask_Id(Long taskId);

    List<TaskEvidence> findByAddedBy_Id(Long userId);
}
