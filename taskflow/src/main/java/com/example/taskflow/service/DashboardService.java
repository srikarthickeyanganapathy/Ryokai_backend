package com.example.taskflow.service;

import java.time.LocalDate;
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

    private final com.github.benmanes.caffeine.cache.Cache<String, DashboardStatsDTO> statsCache = 
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(User user, String scope, Long orgId) {
        String cacheKey = user.getId() + "_" + scope + "_" + (orgId != null ? orgId : "null");
        return statsCache.get(cacheKey, k -> {
            if ("ORG".equalsIgnoreCase(scope) && orgId != null) {
                // Ensure the user actually belongs to this org (basic security check)
                boolean belongsToOrg = membershipRepository.findByUserId(user.getId())
                        .stream().anyMatch(m -> m.getOrganization().getId().equals(orgId));
                
                if (belongsToOrg) {
                    RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
                    // If Director/SuperAdmin, they see the whole org stats
                    if (strategy.canOverride(user)) {
                        return buildStatsForOrg(orgId, user.getId());
                    }
                    // For managers/employees in an ORG, we could limit to their projects or assignments
                    // For now, if they are in the org context, we show them their own org-scoped stats
                    // Actually, ryokai requirements imply that if you select a workspace, you see the stats for it.
                    // If you're a manager, you see stats for tasks you manage within that org.
                    // Since our count queries don't all take orgId right now for managers, we will fallback to org-wide
                    // if they are admins, otherwise just show their assigned tasks in that org.
                    if (strategy.canAssign(user)) {
                         // Fallback to employee for now to ensure data isolation, or build a specific manager org query.
                         // Let's use buildStatsForOrg if we want them to see project progress, 
                         // but to be safe with permissions, let's show their personal assignments + managed if possible.
                         // Currently, we don't have countForManagerAndOrg. We'll fallback to Employee stats for this org.
                         return buildStatsForEmployeeInOrg(user, orgId);
                    }
                    return buildStatsForEmployeeInOrg(user, orgId);
                }
            }
            // Fallback to PERSONAL scope
            return buildStatsForPersonal(user);
        });
    }

    private DashboardStatsDTO buildStatsForOrg(Long orgId, Long userId) {
        LocalDate now = LocalDate.now();
        List<TaskStatus> notApproved = Arrays.asList(TaskStatus.APPROVED);

        long totalTasks = taskRepository.countByOrgIdAndArchivedFalse(orgId);
        long todoCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.IN_PROGRESS);
        long inReviewCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.APPROVED);
        long revisionsCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countByOrgIdOverdue(orgId, now, notApproved);
        long assignedToMeCount = taskRepository.countByAssigneeIdAndArchivedFalse(userId);

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }

    private DashboardStatsDTO buildStatsForEmployeeInOrg(User user, Long orgId) {
        // Since we don't have specific countByAssigneeAndOrgId queries, we will do a rough fallback 
        // to personal stats for now, which guarantees they only see what they own.
        return buildStatsForPersonal(user);
    }

    private DashboardStatsDTO buildStatsForPersonal(User user) {
        LocalDate now = LocalDate.now();
        List<TaskStatus> notApproved = Arrays.asList(TaskStatus.APPROVED);
        Long uid = user.getId();
        
        long totalTasks = taskRepository.countByAssigneeIdAndArchivedFalse(uid);
        long todoCount = taskRepository.countByAssigneeIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.IN_PROGRESS);
        long inReviewCount = taskRepository.countByAssigneeIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countByAssigneeIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.APPROVED);
        long revisionsCount = taskRepository.countByAssigneeIdAndCurrentStatusAndArchivedFalse(uid, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countByAssigneeIdAndDueDateBeforeAndCurrentStatusNotInAndArchivedFalse(uid, now, notApproved);
        
        long assignedToMeCount = totalTasks;

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }


    private DashboardStatsDTO createDto(long total, long todo, long inReview, long done, long revisions, long overdue, long assignedToMe) {
        long denominator = (done + revisions + inReview + todo);
        long completionRate = denominator > 0 ? (done * 100) / denominator : 0;
        
        List<TaskStatusBreakdownDTO> statusBreakdown = new ArrayList<>();
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.IN_PROGRESS.name(), todo, "#FFC107"));
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
