package com.example.taskflow.dashboard.application;

import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.dashboard.dto.DashboardStatsDTO;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.organization.rbac.application.PermissionService;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.team.infrastructure.persistence.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrgDashboardStrategy implements DashboardStatsStrategy {

    private final TaskRepository taskRepository;
    private final PermissionService permissionService;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public boolean supports(String scope) {
        return "ORG".equalsIgnoreCase(scope) || "ORGANIZATION".equalsIgnoreCase(scope);
    }

    @Override
    public DashboardStatsDTO computeStats(User user, Long orgId, Long crewId) {
        if (orgId == null) {
            throw new IllegalArgumentException("Organization ID is required for organization dashboard view");
        }

        boolean isOrgWide = permissionService.isAuthorized(user, PermissionCode.DASHBOARD_VIEW, orgId);
        List<Long> teamScopes = null;
        if (!isOrgWide) {
            teamScopes = teamMemberRepository.findByIdUserId(user.getId()).stream()
                    .filter(tm -> tm.getTeam() != null && tm.getTeam().getOrganization() != null && tm.getTeam().getOrganization().getId().equals(orgId))
                    .map(tm -> tm.getTeam().getId())
                    .collect(java.util.stream.Collectors.toList());
        }

        LocalDate now = LocalDate.now();
        Long userId = user.getId();

        if (teamScopes == null) {
            // Org-wide totals
            long totalTasks = taskRepository.countByOrgIdAndArchivedFalse(orgId);
            long todoCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.IN_PROGRESS);
            long inReviewCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.SUBMITTED);
            long doneCount = taskRepository.countByOrgIdAndCurrentStatusInAndArchivedFalse(orgId, TERMINAL_STATUSES);
            long revisionsCount = taskRepository.countByOrgIdAndCurrentStatusAndArchivedFalse(orgId, TaskStatus.REJECTED);
            long overdueCount = taskRepository.countByOrgIdOverdue(orgId, now, TERMINAL_STATUSES);
            long assignedToMeCount = taskRepository.countByOrgIdAndUser(orgId, userId);

            return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
        } else if (!teamScopes.isEmpty()) {
            // Team-scoped totals
            long totalTasks = taskRepository.countByOrgIdAndTeamIdIn(orgId, teamScopes);
            long todoCount = taskRepository.countByOrgIdAndTeamIdInByStatus(orgId, teamScopes, TaskStatus.IN_PROGRESS);
            long inReviewCount = taskRepository.countByOrgIdAndTeamIdInByStatus(orgId, teamScopes, TaskStatus.SUBMITTED);
            long doneCount = taskRepository.countByOrgIdAndTeamIdInByStatusIn(orgId, teamScopes, TERMINAL_STATUSES);
            long revisionsCount = taskRepository.countByOrgIdAndTeamIdInByStatus(orgId, teamScopes, TaskStatus.REJECTED);
            long overdueCount = taskRepository.countByOrgIdAndTeamIdInOverdue(orgId, teamScopes, now, TERMINAL_STATUSES);
            long assignedToMeCount = taskRepository.countByOrgIdAndUser(orgId, userId);

            return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
        } else {
            // Option B fallback: org member without any team
            long totalTasks = taskRepository.countByOrgIdAndUser(orgId, userId);
            long todoCount = taskRepository.countByOrgIdAndUserByStatus(orgId, userId, TaskStatus.IN_PROGRESS);
            long inReviewCount = taskRepository.countByOrgIdAndUserByStatus(orgId, userId, TaskStatus.SUBMITTED);
            long doneCount = taskRepository.countByOrgIdAndUserByStatusIn(orgId, userId, TERMINAL_STATUSES);
            long revisionsCount = taskRepository.countByOrgIdAndUserByStatus(orgId, userId, TaskStatus.REJECTED);
            long overdueCount = taskRepository.countByOrgIdAndUserOverdue(orgId, userId, now, TERMINAL_STATUSES);
            long assignedToMeCount = totalTasks;

            return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
        }
    }
}