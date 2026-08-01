package com.example.taskflow.task.application.query;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.user.dto.UserSummaryDTO;
import com.example.taskflow.task.api.response.WorkloadDTOs.UserWorkloadDTO;
import com.example.taskflow.organization.core.exception.OrganizationSuspendedException;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.security.PermissionCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkloadService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TaskRepository taskRepository;
    private final AuthorizationEngine authorizationEngine;

    public List<UserWorkloadDTO> getWorkloadMatrix(User requester, Long orgId) {
        var org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (org.getStatus() != Organization.OrgStatus.ACTIVE) {
            throw new OrganizationSuspendedException("Organization is not active.");
        }

        boolean isAuthorized = authorizationEngine.authorize(com.example.taskflow.security.authorization.AuthorizationRequest.builder(requester, PermissionCode.DASHBOARD_VIEW).context(java.util.Map.of("organizationId", orgId)).requiredScope(com.example.taskflow.security.ScopeType.ORGANIZATION).build()).isGranted() ||
                               authorizationEngine.authorize(com.example.taskflow.security.authorization.AuthorizationRequest.builder(requester, PermissionCode.TASK_VIEW).context(java.util.Map.of("organizationId", orgId)).requiredScope(com.example.taskflow.security.ScopeType.ORGANIZATION).build()).isGranted();
        
        if (!isAuthorized) {
            throw new UnauthorizedActionException(
                    "You are not authorized to view the workload matrix.");
        }

        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);
        List<Object[]> counts = taskRepository.countTasksByOrgGroupedByAssigneeAndStatus(orgId);

        return members.stream().map(m -> {
            User u = m.getUser();
            long todo = 0, inProgress = 0, submitted = 0, approved = 0, rejected = 0;

            for (Object[] row : counts) {
                Long assigneeId = row[0] != null ? ((Number) row[0]).longValue() : null;
                if (assigneeId != null && assigneeId.equals(u.getId())) {
                    TaskStatus status = null;
                    if (row[1] instanceof TaskStatus ts) {
                        status = ts;
                    } else if (row[1] instanceof String str) {
                        try {
                            status = TaskStatus.valueOf(str);
                        } catch (Exception ignored) {}
                    } else if (row[1] instanceof Number num) {
                        status = TaskStatus.values()[num.intValue()];
                    }

                    long count = row[2] != null ? ((Number) row[2]).longValue() : 0;
                    if (status != null) {
                        switch (status) {
                            case TODO -> todo = count;
                            case IN_PROGRESS -> inProgress = count;
                            case SUBMITTED -> submitted = count;
                            case APPROVED -> approved = count;
                            case REJECTED -> rejected = count;
                            default -> {}
                        }
                    }
                }
            }

            UserSummaryDTO summary = new UserSummaryDTO(u.getId(), u.getUsername());
            return new UserWorkloadDTO(summary, todo, inProgress, submitted, approved, rejected);
        }).toList();
    }
}