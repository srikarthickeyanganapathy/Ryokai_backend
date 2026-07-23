package com.example.taskflow.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.TaskActivityLog;

@Repository
public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, Long> {
    Page<TaskActivityLog> findByTaskIdOrderByCreatedAtDesc(Long taskId, Pageable pageable);
}
