package com.example.taskflow.project.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.project.domain.ProjectActivityLog;

@Repository
public interface ProjectActivityLogRepository extends JpaRepository<ProjectActivityLog, Long> {
    Page<ProjectActivityLog> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);
}
