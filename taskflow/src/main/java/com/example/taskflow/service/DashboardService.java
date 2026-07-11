package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import com.example.taskflow.domain.TaskStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Task;
import com.example.taskflow.dto.DashboardStatsDTO;
import com.example.taskflow.dto.TaskStatusBreakdownDTO;
import com.example.taskflow.dto.ActivityEventDTO;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.security.RoleStrategy;
import com.example.taskflow.security.RoleStrategyFactory;

@Service
public class DashboardService {

    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;
    private final RoleStrategyFactory roleStrategyFactory;
    private final com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository;

    public DashboardService(TaskRepository taskRepository, TaskAuditService taskAuditService,
                            RoleStrategyFactory roleStrategyFactory,
                            com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.roleStrategyFactory = roleStrategyFactory;
        this.membershipRepository = membershipRepository;
    }

    private final com.github.benmanes.caffeine.cache.Cache<Long, DashboardStatsDTO> statsCache = 
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(User user) {
        return statsCache.get(user.getId(), k -> {
            RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
            if (strategy.canOverride(user)) {
                return buildStatsForAllUsers(user);
            }
            if (strategy.canAssign(user)) {
                return buildStatsForManager(user);
            }
            return buildStatsForEmployee(user);
        });
    }

    /**
     * Fix #4: Scoped to user's organization. Only Super Admin sees platform-wide stats.
     */
    private DashboardStatsDTO buildStatsForAllUsers(User user) {
        LocalDateTime now = LocalDateTime.now();
        List<TaskStatus> notApproved = Arrays.asList(TaskStatus.APPROVED);

        // Check if Super Admin
        boolean isSuperAdmin = user.getRoles().stream()
                .anyMatch(r -> {
                    String name = r.getName();
                    if (name.startsWith("ROLE_")) name = name.substring(5);
                    return "SUPER_ADMIN".equals(name);
                });

        if (isSuperAdmin) {
            // Super Admin: privacy boundary — show only own personal task stats
            // Platform-level metadata (orgs, users) is available via /api/admin endpoints
            return buildStatsForEmployee(user);
        }

        // Director/Admin: scope to their own org
        var memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            // Fallback: no org — show only own tasks
            return buildStatsForEmployee(user);
        }

        Long orgId = memberships.get(0).getOrganization().getId();
        long totalTasks = taskRepository.countByOrganizationIdAndArchivedFalse(orgId);
        long todoCount = taskRepository.countByOrganizationIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.ASSIGNED);
        long inReviewCount = taskRepository.countByOrganizationIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countByOrganizationIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.APPROVED);
        long revisionsCount = taskRepository.countByOrganizationIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countByOrgIdOverdue(orgId, now, notApproved);
        long assignedToMeCount = taskRepository.countByAssignedToIdAndArchivedFalse(user.getId());

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }

    private DashboardStatsDTO buildStatsForManager(User user) {
        LocalDateTime now = LocalDateTime.now();
        List<TaskStatus> notApproved = Arrays.asList(TaskStatus.APPROVED);
        Long uid = user.getId();
        
        long totalTasks = taskRepository.countForManager(uid);
        long todoCount = taskRepository.countForManagerByStatus(uid, TaskStatus.ASSIGNED);
        long inReviewCount = taskRepository.countForManagerByStatus(uid, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countForManagerByStatus(uid, TaskStatus.APPROVED);
        long revisionsCount = taskRepository.countForManagerByStatus(uid, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countForManagerOverdue(uid, now, notApproved);
        
        long assignedToMeCount = taskRepository.countByAssignedToIdAndArchivedFalse(uid);

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }

    private DashboardStatsDTO buildStatsForEmployee(User user) {
        LocalDateTime now = LocalDateTime.now();
        List<TaskStatus> notApproved = Arrays.asList(TaskStatus.APPROVED);
        Long uid = user.getId();
        
        long totalTasks = taskRepository.countByAssignedToIdAndArchivedFalse(uid);
        long todoCount = taskRepository.countByAssignedToIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.ASSIGNED);
        long inReviewCount = taskRepository.countByAssignedToIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countByAssignedToIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.APPROVED);
        long revisionsCount = taskRepository.countByAssignedToIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countByAssignedToIdAndDueDateBeforeAndCurrentStatusNotInAndArchivedFalse(uid, now, notApproved);
        
        long assignedToMeCount = totalTasks;

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }

    private DashboardStatsDTO createDto(long total, long todo, long inReview, long done, long revisions, long overdue, long assignedToMe) {
        long denominator = (done + revisions + inReview + todo);
        long completionRate = denominator > 0 ? (done * 100) / denominator : 0;
        
        List<TaskStatusBreakdownDTO> statusBreakdown = new ArrayList<>();
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.ASSIGNED.name(), todo, "#FFC107"));
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.SUBMITTED.name(), inReview, "#17A2B8"));
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.APPROVED.name(), done, "#28A745"));
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.REJECTED.name(), revisions, "#DC3545"));
        
        return new DashboardStatsDTO(total, todo, inReview, done, revisions, overdue, assignedToMe, statusBreakdown, completionRate);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventDTO> getActivityFeed(User user, Pageable pageable, boolean includeAllTypes) {
        return taskAuditService.getGlobalActivityFeed(user, pageable, includeAllTypes);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventDTO> getActivityFeedForTask(Long taskId, User user, Pageable pageable) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new com.example.taskflow.exception.TaskNotFoundException("Task not found"));

        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
        
        if (!strategy.canViewTask(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view this task's history.");
        }

        return taskAuditService.getActivityFeedForTask(taskId, pageable);
    }
}
