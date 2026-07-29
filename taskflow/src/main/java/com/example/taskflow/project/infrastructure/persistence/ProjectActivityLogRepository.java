package com.example.taskflow.project.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.project.domain.ProjectActivityLog;

public interface ProjectActivityLogRepository extends JpaRepository<ProjectActivityLog, Long> {
    Page<ProjectActivityLog> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
}
