package com.example.taskflow.repository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.domain.TaskStatusHistory;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findByTask_Id(Long taskId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.eventType != 'COMMENTED' AND h.eventType != 'CHECKLIST_TOGGLED' AND h.task.id IN " +
            "(SELECT t.id FROM Task t WHERE t.assignee.id = :userId OR t.creator.id = :userId) " +
            "ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findGlobalFeedForUser(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.eventType != 'COMMENTED' AND h.eventType != 'CHECKLIST_TOGGLED' AND h.task.id IN " +
            "(SELECT t.id FROM Task t WHERE t.assignee.id = :userId " +
            "OR t.creator.id = :userId) " +
            "ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findManagerFeed(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.eventType != 'COMMENTED' AND h.eventType != 'CHECKLIST_TOGGLED' ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findAllFeed(Pageable pageable);

    // Queries that include comments/checklists
    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.task.id IN " +
            "(SELECT t.id FROM Task t WHERE t.assignee.id = :userId OR t.creator.id = :userId) " +
            "ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findGlobalFeedForUserAllTypes(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.task.id IN " +
            "(SELECT t.id FROM Task t WHERE t.assignee.id = :userId " +
            "OR t.creator.id = :userId) " +
            "ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findManagerFeedAllTypes(@org.springframework.data.repository.query.Param("userId") Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findAllFeedAllTypes(Pageable pageable);

    // Org-scoped feed queries (Fix #4: data isolation for Director/Admin)
    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.eventType != 'COMMENTED' AND h.eventType != 'CHECKLIST_TOGGLED' AND h.task.org.id = :orgId ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findOrgFeed(@org.springframework.data.repository.query.Param("orgId") Long orgId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM TaskStatusHistory h WHERE h.task.org.id = :orgId ORDER BY h.changedAt DESC")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"task", "changedBy"})
    Page<TaskStatusHistory> findOrgFeedAllTypes(@org.springframework.data.repository.query.Param("orgId") Long orgId, Pageable pageable);

    void deleteByTaskId(Long taskId);
}
