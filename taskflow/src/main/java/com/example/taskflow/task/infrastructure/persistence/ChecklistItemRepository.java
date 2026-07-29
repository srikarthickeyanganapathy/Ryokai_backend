package com.example.taskflow.task.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.task.domain.model.ChecklistItem;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    // Find all items for a specific task
    List<ChecklistItem> findByTaskIdOrderByDisplayOrderAsc(Long taskId);
    void deleteByTaskId(Long taskId);
}