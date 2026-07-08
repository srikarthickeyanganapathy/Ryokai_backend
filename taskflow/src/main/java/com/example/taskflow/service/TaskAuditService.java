package com.example.taskflow.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskStatusHistory;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ActivityEventDTO;
import com.example.taskflow.dto.UserSummaryDTO;
import com.example.taskflow.repository.TaskStatusHistoryRepository;
import com.example.taskflow.security.RoleStrategy;
import com.example.taskflow.security.RoleStrategyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.example.taskflow.util.RelativeTimeFormatter;

@Service
public class TaskAuditService {

    private final TaskStatusHistoryRepository historyRepository;
    private final RoleStrategyFactory roleStrategyFactory;
    private final com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository;

    public TaskAuditService(TaskStatusHistoryRepository historyRepository, RoleStrategyFactory roleStrategyFactory,
                            com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository) {
        this.historyRepository = historyRepository;
        this.roleStrategyFactory = roleStrategyFactory;
        this.membershipRepository = membershipRepository;
    }

    // Full signature
    @Transactional
    public void recordStatus(Task task, String fromStatus, String toStatus, String eventType, User actor, String reason) {
        TaskStatusHistory h = new TaskStatusHistory();
        h.setTask(task);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setStatus(toStatus != null ? toStatus : task.getCurrentStatus().name()); // keep old field for backwards compat during migration
        h.setEventType(eventType);
        h.setReason(reason);
        h.setChangedBy(actor);
        h.setChangedAt(LocalDateTime.now());
        h.setTaskTitleSnapshot(task.getTitle());
        h.setActorUsernameSnapshot(actor.getUsername());
        h.setAssigneeUsernameSnapshot(task.getAssignedTo() != null ? task.getAssignedTo().getUsername() : null);
        h.setCreatorUsernameSnapshot(task.getCreatedBy() != null ? task.getCreatedBy().getUsername() : null);
        historyRepository.save(h);
    }

    // Convenience overload — infers fromStatus from task's current status BEFORE mutation
    @Transactional
    public void recordStatus(Task task, String toStatus, String eventType, User actor, String reason) {
        recordStatus(task, task.getCurrentStatus().name(), toStatus, eventType, actor, reason);
    }

    // Overload for backward compatibility / simple status changes
    @Transactional
    public void recordStatus(Task task, String status, User user) {
        recordStatus(task, task.getCurrentStatus().name(), status, "STATUS_CHANGE", user, null);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventDTO> getActivityFeedForTask(Long taskId, Pageable pageable) {
        return historyRepository.findByTask_Id(taskId, pageable)
                .map(this::mapToActivityEventDTO);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventDTO> getGlobalActivityFeed(User user, Pageable pageable, boolean includeAllTypes) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);

        if (strategy.canOverride(user)) {
            // Check if SUPER_ADMIN vs org ADMIN/DIRECTOR
            boolean isSuperAdmin = user.getRoles().stream()
                    .anyMatch(r -> {
                        String name = r.getName();
                        if (name.startsWith("ROLE_")) name = name.substring(5);
                        return "SUPER_ADMIN".equals(name);
                    });

            if (isSuperAdmin) {
                // Super Admin sees all platform activity
                return includeAllTypes
                    ? historyRepository.findAllFeedAllTypes(pageable).map(this::mapToActivityEventDTO)
                    : historyRepository.findAllFeed(pageable).map(this::mapToActivityEventDTO);
            }

            // Director/Admin: scope to their org
            var memberships = membershipRepository.findByUserId(user.getId());
            if (!memberships.isEmpty()) {
                Long orgId = memberships.get(0).getOrganization().getId();
                return includeAllTypes
                    ? historyRepository.findOrgFeedAllTypes(orgId, pageable).map(this::mapToActivityEventDTO)
                    : historyRepository.findOrgFeed(orgId, pageable).map(this::mapToActivityEventDTO);
            }
        }

        if (strategy.canAssign(user)) {
            // Manager sees team's + own
            return includeAllTypes
                ? historyRepository.findManagerFeedAllTypes(user.getId(), pageable).map(this::mapToActivityEventDTO)
                : historyRepository.findManagerFeed(user.getId(), pageable).map(this::mapToActivityEventDTO);
        }

        // Employee sees only own
        return includeAllTypes
            ? historyRepository.findGlobalFeedForUserAllTypes(user.getId(), pageable).map(this::mapToActivityEventDTO)
            : historyRepository.findGlobalFeedForUser(user.getId(), pageable).map(this::mapToActivityEventDTO);
    }

    private ActivityEventDTO mapToActivityEventDTO(TaskStatusHistory history) {
        return new ActivityEventDTO(
                history.getId(),
                history.getTask().getId(),
                history.getTaskTitleSnapshot() != null ? history.getTaskTitleSnapshot() : history.getTask().getTitle(),
                history.getEventType(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getReason(),
                new UserSummaryDTO(history.getChangedBy().getId(), 
                        history.getActorUsernameSnapshot() != null ? history.getActorUsernameSnapshot() : history.getChangedBy().getUsername()),
                history.getChangedAt(),
                RelativeTimeFormatter.format(history.getChangedAt())
        );
    }
}
