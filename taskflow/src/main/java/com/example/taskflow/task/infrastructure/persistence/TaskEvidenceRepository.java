package com.example.taskflow.task.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.task.domain.model.TaskEvidence;

@Repository
public interface TaskEvidenceRepository extends JpaRepository<TaskEvidence, Long> {

    List<TaskEvidence> findByTask_Id(Long taskId);

    List<TaskEvidence> findByAddedBy_Id(Long userId);

    void deleteByTask_Id(Long taskId);
}
