package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Override
    Page<Task> findAll(Pageable pageable);

    // Used by Employees to see only their assigned tasks
    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    Page<Task> findByAssignedTo(User user, Pageable pageable);

    // Used for reassignment during leave — non-paginated
    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    List<Task> findByAssignedTo(User user);

    // Used by Managers to see tasks they assigned OR tasks assigned to them
    // This supports the "Review" workflow where a Manager needs to see tasks they created
    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Query("SELECT t FROM Task t WHERE t.assignedTo = :user OR t.createdBy = :user")
    Page<Task> findByAssignedToOrCreatedBy(@Param("user") User user, Pageable pageable);

    // B-12: Manager/Employee queries with personal-task filter at DB level (fixes pagination metadata)
    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Query("SELECT t FROM Task t WHERE (t.assignedTo = :user OR t.createdBy = :user) " +
           "AND (t.isPersonal = false OR (t.isPersonal = true AND t.createdBy = :user))")
    Page<Task> findVisibleForManager(@Param("user") User user, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Query("SELECT t FROM Task t WHERE t.assignedTo = :user " +
           "AND (t.isPersonal = false OR (t.isPersonal = true AND t.createdBy = :user))")
    Page<Task> findVisibleForEmployee(@Param("user") User user, Pageable pageable);

    // Employee counts
    long countByAssignedToIdAndArchivedFalse(Long userId);
    long countByAssignedToIdAndCurrentStatusAndArchivedFalse(Long userId, TaskStatus status);
    long countByAssignedToIdAndDueDateBeforeAndCurrentStatusNotInAndArchivedFalse(Long userId, java.time.LocalDateTime date, java.util.Collection<TaskStatus> statuses);

    // Director counts
    long countByArchivedFalse();
    long countByCurrentStatusAndArchivedFalse(TaskStatus status);
    long countByDueDateBeforeAndCurrentStatusNotInAndArchivedFalse(java.time.LocalDateTime date, java.util.Collection<TaskStatus> statuses);

    // Manager counts
    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND (t.assignedTo.id = :userId OR t.createdBy.id = :userId)")
    long countForManager(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.currentStatus = :status AND (t.assignedTo.id = :userId OR t.createdBy.id = :userId)")
    long countForManagerByStatus(@Param("userId") Long userId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.dueDate < :date AND t.currentStatus NOT IN :statuses AND (t.assignedTo.id = :userId OR t.createdBy.id = :userId)")
    long countForManagerOverdue(@Param("userId") Long userId, @Param("date") java.time.LocalDateTime date, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Query("SELECT t FROM Task t WHERE t.dueDate BETWEEN :from AND :to " +
           "AND t.currentStatus NOT IN :excludeStatuses AND t.archived = false")
    Page<Task> findDueSoon(@Param("from") java.time.LocalDateTime from,
                           @Param("to") java.time.LocalDateTime to,
                           @Param("excludeStatuses") List<TaskStatus> excludeStatuses,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Query("SELECT t FROM Task t WHERE t.dueDate < :now " +
           "AND t.currentStatus NOT IN :excludeStatuses AND t.archived = false")
    Page<Task> findOverdue(@Param("now") java.time.LocalDateTime now,
                           @Param("excludeStatuses") List<TaskStatus> excludeStatuses,
                           Pageable pageable);

    // Org-scoped task queries (Fix #3: data isolation for Director/Admin)
    @EntityGraph(attributePaths = {"assignedTo","createdBy","reviewedBy","organization","team","project"})
    @Query("SELECT t FROM Task t WHERE (t.organization.id = :orgId) OR (t.organization IS NULL AND t.createdBy = :user)")
    Page<Task> findByOrganizationIdOrCreatedBy(@Param("orgId") Long orgId, @Param("user") User user, Pageable pageable);

    // Org-scoped count methods for DashboardService (Fix #4)
    long countByOrganizationIdAndArchivedFalse(Long orgId);

    long countByOrganizationIdAndCurrentStatusAndArchivedFalse(Long orgId, TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.organization.id = :orgId AND t.archived = false " +
           "AND t.dueDate < :date AND t.currentStatus NOT IN :statuses")
    long countByOrgIdOverdue(@Param("orgId") Long orgId, @Param("date") java.time.LocalDateTime date,
                             @Param("statuses") java.util.Collection<TaskStatus> statuses);

    // Project-scoped counts
    long countByProjectId(Long projectId);
    long countByProjectIdAndCurrentStatus(Long projectId, TaskStatus status);
    long countByProjectIdAndCurrentStatusIn(Long projectId, java.util.Collection<TaskStatus> statuses);
    
    // Team-scoped counts
    @Query("SELECT COUNT(t) FROM Task t WHERE t.team.id = :teamId")
    long countByTeamId(@Param("teamId") Long teamId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Task t SET t.project = NULL WHERE t.project.id = :projectId")
    void detachProjectFromTasks(@Param("projectId") Long projectId);
}