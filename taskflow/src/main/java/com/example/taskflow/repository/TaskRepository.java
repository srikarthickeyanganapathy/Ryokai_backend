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

    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    @Override
    Page<Task> findAll(Pageable pageable);

    // Used by Employees to see only their assigned tasks
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    Page<Task> findByAssignee(User user, Pageable pageable);

    // Used for reassignment during leave  -  non-paginated
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    List<Task> findByAssignee(User user);

    // Used by Managers to see tasks they assigned OR tasks assigned to them
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    @Query("SELECT t FROM Task t WHERE t.assignee = :user OR t.creator = :user")
    Page<Task> findByAssigneeOrCreator(@Param("user") User user, Pageable pageable);

    // B-12: Manager/Employee queries with personal-task filter at DB level.
    // Updated: Now includes tasks from Teams the user is a member or observer of,
    // and Projects belonging to those Teams.
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project","crew"})
    @Query("SELECT t FROM Task t WHERE " +
           "(t.assignee = :user OR t.creator = :user) " +
           "OR (t.crew.id IN (SELECT cm.id.crewId FROM CrewMember cm WHERE cm.id.userId = :userId)) " +
           "OR (t.team.id IN (SELECT tm.id.teamId FROM TeamMember tm WHERE tm.id.userId = :userId)) " +
           "OR (t.project.team.id IN (SELECT tm.id.teamId FROM TeamMember tm WHERE tm.id.userId = :userId)) " +
           "OR (t.team.id IN (SELECT tobs.id.teamId FROM TeamObserver tobs WHERE tobs.id.userId = :userId)) " +
           "OR (t.project.team.id IN (SELECT tobs.id.teamId FROM TeamObserver tobs WHERE tobs.id.userId = :userId))")
    Page<Task> findVisibleForManager(@Param("user") User user, @Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project","crew"})
    @Query("SELECT t FROM Task t WHERE " +
           "(t.assignee = :user OR t.creator = :user) " +
           "OR (t.crew.id IN (SELECT cm.id.crewId FROM CrewMember cm WHERE cm.id.userId = :userId)) " +
           "OR (t.team.id IN (SELECT tm.id.teamId FROM TeamMember tm WHERE tm.id.userId = :userId)) " +
           "OR (t.project.team.id IN (SELECT tm.id.teamId FROM TeamMember tm WHERE tm.id.userId = :userId)) " +
           "OR (t.team.id IN (SELECT tobs.id.teamId FROM TeamObserver tobs WHERE tobs.id.userId = :userId)) " +
           "OR (t.project.team.id IN (SELECT tobs.id.teamId FROM TeamObserver tobs WHERE tobs.id.userId = :userId))")
    Page<Task> findVisibleForEmployee(@Param("user") User user, @Param("userId") Long userId, Pageable pageable);

    /** Crew-scoped listing: all tasks in a crew (caller must already be a member). */
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project","crew"})
    @Query("SELECT t FROM Task t WHERE t.crew.id = :crewId")
    Page<Task> findByCrewId(@Param("crewId") Long crewId, Pageable pageable);
    
    boolean existsByCrewId(Long crewId);
    
    // Bug #6/#8 Fix: Check if a user has non-terminal tasks in a team.
    // Org tasks terminate at APPROVED, personal/crew at COMPLETED — must exclude both.
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Task t " +
           "WHERE t.team.id = :teamId AND t.assignee.id = :assigneeId " +
           "AND t.currentStatus NOT IN (com.example.taskflow.domain.TaskStatus.COMPLETED, com.example.taskflow.domain.TaskStatus.APPROVED)")
    boolean existsByTeamIdAndAssigneeIdAndNonTerminalStatus(@Param("teamId") Long teamId, @Param("assigneeId") Long assigneeId);

    // Employee counts
    long countByAssigneeIdAndArchivedFalse(Long userId);
    long countByAssigneeIdAndCurrentStatusAndArchivedFalse(Long userId, TaskStatus status);
    long countByAssigneeIdAndCurrentStatusInAndArchivedFalse(Long userId, java.util.Collection<TaskStatus> statuses);
    long countByAssigneeIdAndDueDateBeforeAndCurrentStatusNotInAndArchivedFalse(Long userId, java.time.LocalDate date, java.util.Collection<TaskStatus> statuses);

    // Director counts
    long countByArchivedFalse();
    long countByCurrentStatusAndArchivedFalse(TaskStatus status);
    long countByDueDateBeforeAndCurrentStatusNotInAndArchivedFalse(java.time.LocalDate date, java.util.Collection<TaskStatus> statuses);

    // Manager counts
    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND (t.assignee.id = :userId OR t.creator.id = :userId)")
    long countForManager(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.currentStatus = :status AND (t.assignee.id = :userId OR t.creator.id = :userId)")
    long countForManagerByStatus(@Param("userId") Long userId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.dueDate < :date AND t.currentStatus NOT IN :statuses AND (t.assignee.id = :userId OR t.creator.id = :userId)")
    long countForManagerOverdue(@Param("userId") Long userId, @Param("date") java.time.LocalDate date, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    @Query("SELECT t FROM Task t WHERE t.dueDate BETWEEN :from AND :to " +
           "AND t.currentStatus NOT IN :excludeStatuses AND t.archived = false")
    Page<Task> findDueSoon(@Param("from") java.time.LocalDate from,
                           @Param("to") java.time.LocalDate to,
                           @Param("excludeStatuses") List<TaskStatus> excludeStatuses,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    @Query("SELECT t FROM Task t WHERE t.dueDate < :now " +
           "AND t.currentStatus NOT IN :excludeStatuses AND t.archived = false")
    Page<Task> findOverdue(@Param("now") java.time.LocalDate now,
                           @Param("excludeStatuses") List<TaskStatus> excludeStatuses,
                           Pageable pageable);

    // Org-scoped task queries (Fix #3: data isolation for Director/Admin)
    // P1: also include crew tasks for crews the user belongs to
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project","crew"})
    @Query("SELECT t FROM Task t WHERE (t.org.id = :orgId) " +
           "OR (t.org IS NULL AND t.crew IS NULL AND t.creator = :user) " +
           "OR (t.crew.id IN (SELECT cm.id.crewId FROM CrewMember cm WHERE cm.id.userId = :userId))")
    Page<Task> findByOrganizationIdOrCreatedBy(@Param("orgId") Long orgId, @Param("user") User user,
                                               @Param("userId") Long userId, Pageable pageable);

    // Org-scoped count methods for DashboardService (Fix #4)
    long countByOrgIdAndArchivedFalse(Long orgId);

    long countByOrgIdAndCurrentStatusAndArchivedFalse(Long orgId, TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND t.archived = false " +
           "AND t.dueDate < :date AND t.currentStatus NOT IN :statuses")
    long countByOrgIdOverdue(@Param("orgId") Long orgId, @Param("date") java.time.LocalDate date,
                             @Param("statuses") java.util.Collection<TaskStatus> statuses);

    // Project-scoped counts
    long countByProjectId(Long projectId);
    long countByProjectIdAndCurrentStatus(Long projectId, TaskStatus status);
    long countByProjectIdAndCurrentStatusIn(Long projectId, java.util.Collection<TaskStatus> statuses);

    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project"})
    Page<Task> findByProjectId(Long projectId, Pageable pageable);
    
    // Team-scoped counts
    @Query("SELECT COUNT(t) FROM Task t WHERE t.team.id = :teamId")
    long countByTeamId(@Param("teamId") Long teamId);

    @Query("SELECT t FROM Task t WHERE t.org.id = :orgId")
    List<Task> findByOrgId(@Param("orgId") Long orgId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Task t SET t.project = NULL WHERE t.project.id = :projectId")
    void detachProjectFromTasks(@Param("projectId") Long projectId);

    @Query("SELECT t.assignee.id, t.currentStatus, COUNT(t) FROM Task t WHERE t.org.id = :orgId AND t.archived = false GROUP BY t.assignee.id, t.currentStatus")
    List<Object[]> countTasksByOrgGroupedByAssigneeAndStatus(@Param("orgId") Long orgId);

    // Crew tasks with bridge traversal
    @EntityGraph(attributePaths = {"assignee","creator","reviewer","org","team","project","crew"})
    @Query("SELECT DISTINCT t FROM Task t WHERE t.archived = false AND (t.crew.id = :crewId OR t.project IN (SELECT p FROM Project p JOIN p.sharedCrews sc WHERE sc.id = :crewId AND p.deleted = false AND p.organization IS NULL))")
    Page<Task> findByCrewIdWithBridge(@Param("crewId") Long crewId, Pageable pageable);

    // Personal counts for Dashboard
    @Query("SELECT COUNT(t) FROM Task t WHERE (t.assignee.id = :userId OR t.creator.id = :userId) AND t.org IS NULL AND t.crew IS NULL AND t.archived = false")
    long countPersonalTasks(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE (t.assignee.id = :userId OR t.creator.id = :userId) AND t.org IS NULL AND t.crew IS NULL AND t.currentStatus = :status AND t.archived = false")
    long countPersonalTasksByStatus(@Param("userId") Long userId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE (t.assignee.id = :userId OR t.creator.id = :userId) AND t.org IS NULL AND t.crew IS NULL AND t.currentStatus IN :statuses AND t.archived = false")
    long countPersonalTasksByStatusIn(@Param("userId") Long userId, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE (t.assignee.id = :userId OR t.creator.id = :userId) AND t.org IS NULL AND t.crew IS NULL AND t.dueDate < :date AND t.currentStatus NOT IN :statuses AND t.archived = false")
    long countPersonalTasksOverdue(@Param("userId") Long userId, @Param("date") java.time.LocalDate date, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    // Org-wide counts (additional)
    long countByOrgIdAndCurrentStatusInAndArchivedFalse(Long orgId, java.util.Collection<TaskStatus> statuses);

    // Team-scoped counts in Org (Multi-team support via teamId IN)
    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND t.team.id IN :teamIds AND t.archived = false")
    long countByOrgIdAndTeamIdIn(@Param("orgId") Long orgId, @Param("teamIds") java.util.Collection<Long> teamIds);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND t.team.id IN :teamIds AND t.currentStatus = :status AND t.archived = false")
    long countByOrgIdAndTeamIdInByStatus(@Param("orgId") Long orgId, @Param("teamIds") java.util.Collection<Long> teamIds, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND t.team.id IN :teamIds AND t.currentStatus IN :statuses AND t.archived = false")
    long countByOrgIdAndTeamIdInByStatusIn(@Param("orgId") Long orgId, @Param("teamIds") java.util.Collection<Long> teamIds, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND t.team.id IN :teamIds AND t.dueDate < :date AND t.currentStatus NOT IN :statuses AND t.archived = false")
    long countByOrgIdAndTeamIdInOverdue(@Param("orgId") Long orgId, @Param("teamIds") java.util.Collection<Long> teamIds, @Param("date") java.time.LocalDate date, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    // Option B fallback counts in Org (member without a team)
    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND (t.assignee.id = :userId OR t.creator.id = :userId) AND t.archived = false")
    long countByOrgIdAndUser(@Param("orgId") Long orgId, @Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND (t.assignee.id = :userId OR t.creator.id = :userId) AND t.currentStatus = :status AND t.archived = false")
    long countByOrgIdAndUserByStatus(@Param("orgId") Long orgId, @Param("userId") Long userId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND (t.assignee.id = :userId OR t.creator.id = :userId) AND t.currentStatus IN :statuses AND t.archived = false")
    long countByOrgIdAndUserByStatusIn(@Param("orgId") Long orgId, @Param("userId") Long userId, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.org.id = :orgId AND (t.assignee.id = :userId OR t.creator.id = :userId) AND t.dueDate < :date AND t.currentStatus NOT IN :statuses AND t.archived = false")
    long countByOrgIdAndUserOverdue(@Param("orgId") Long orgId, @Param("userId") Long userId, @Param("date") java.time.LocalDate date, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    // Crew counts with bridge traversal
    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND (t.crew.id = :crewId OR t.project IN (SELECT p FROM Project p JOIN p.sharedCrews sc WHERE sc.id = :crewId AND p.deleted = false AND p.organization IS NULL))")
    long countForCrew(@Param("crewId") Long crewId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.currentStatus = :status AND (t.crew.id = :crewId OR t.project IN (SELECT p FROM Project p JOIN p.sharedCrews sc WHERE sc.id = :crewId AND p.deleted = false AND p.organization IS NULL))")
    long countForCrewByStatus(@Param("crewId") Long crewId, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.currentStatus IN :statuses AND (t.crew.id = :crewId OR t.project IN (SELECT p FROM Project p JOIN p.sharedCrews sc WHERE sc.id = :crewId AND p.deleted = false AND p.organization IS NULL))")
    long countForCrewByStatusIn(@Param("crewId") Long crewId, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.dueDate < :date AND t.currentStatus NOT IN :statuses AND (t.crew.id = :crewId OR t.project IN (SELECT p FROM Project p JOIN p.sharedCrews sc WHERE sc.id = :crewId AND p.deleted = false AND p.organization IS NULL))")
    long countForCrewOverdue(@Param("crewId") Long crewId, @Param("date") java.time.LocalDate date, @Param("statuses") java.util.Collection<TaskStatus> statuses);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.archived = false AND t.assignee.id = :userId AND (t.crew.id = :crewId OR t.project IN (SELECT p FROM Project p JOIN p.sharedCrews sc WHERE sc.id = :crewId AND p.deleted = false AND p.organization IS NULL))")
    long countForCrewAndAssignee(@Param("crewId") Long crewId, @Param("userId") Long userId);
}