package com.example.taskflow.dashboard.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.dashboard.dto.DashboardStatsDTO;
import com.example.taskflow.dashboard.dto.ActivityEventDTO;
import com.example.taskflow.task.application.orchestration.TaskAuditService;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.task.security.TaskPermissionHandler;

@Service
public class DashboardQueryService {

    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;
    private final TaskPermissionHandler taskPermissionHandler;
    private final com.example.taskflow.dashboard.application.DashboardStrategyFactory dashboardStrategyFactory;

    public DashboardQueryService(TaskRepository taskRepository, TaskAuditService taskAuditService,
                            TaskPermissionHandler taskPermissionHandler,
                            com.example.taskflow.dashboard.application.DashboardStrategyFactory dashboardStrategyFactory) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.taskPermissionHandler = taskPermissionHandler;
        this.dashboardStrategyFactory = dashboardStrategyFactory;
    }

    private final com.github.benmanes.caffeine.cache.Cache<String, DashboardStatsDTO> statsCache = 
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(User user, String scope, Long orgId, Long crewId) {
        String cacheKey = user.getId() + "_" + scope + "_" + (orgId != null ? orgId : "null") + "_" + (crewId != null ? crewId : "null");
        return statsCache.get(cacheKey, k -> dashboardStrategyFactory.getStrategy(scope).computeStats(user, orgId, crewId));
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(User user, String scope, Long orgId) {
        return getStats(user, scope, orgId, null);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventDTO> getActivityFeed(User user, Pageable pageable, boolean includeAllTypes) {
        return taskAuditService.getGlobalActivityFeed(user, pageable, includeAllTypes);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventDTO> getActivityFeedForTask(Long taskId, User user, Pageable pageable) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new com.example.taskflow.task.exception.TaskNotFoundException("Task not found"));

        if (!taskPermissionHandler.hasPermission(null, user, task, "VIEW")) {
            throw new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not authorized to view this task's history.");
        }

        return taskAuditService.getActivityFeedForTask(taskId, pageable);
    }
}