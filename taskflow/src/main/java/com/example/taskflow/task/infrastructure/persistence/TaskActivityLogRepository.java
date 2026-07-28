package com.example.taskflow.task.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.task.domain.model.TaskActivityLog;

@Repository
public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, Long> {
    Page<TaskActivityLog> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);
}
