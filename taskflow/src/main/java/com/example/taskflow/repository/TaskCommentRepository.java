package com.example.taskflow.repository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.domain.TaskComment;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    // Spring Data JPA automatically derives the query for task.id
    Page<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId, Pageable pageable);
    void deleteByTaskId(Long taskId);
}