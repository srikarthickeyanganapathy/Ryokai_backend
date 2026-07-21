package com.example.taskflow.service;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.UserSummaryDTO;
import com.example.taskflow.dto.WorkloadDTOs.UserWorkloadDTO;
import com.example.taskflow.exception.OrganizationSuspendedException;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.security.RoleStrategy;
import com.example.taskflow.security.RoleStrategyFactory;
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
    private final RoleStrategyFactory roleStrategyFactory;

    public List<UserWorkloadDTO> getWorkloadMatrix(User requester, Long orgId) {
        var org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (org.getStatus() != Organization.OrgStatus.ACTIVE) {
            throw new OrganizationSuspendedException("Organization is not active.");
        }

        boolean isMember = membershipRepository.existsByUserAndOrganization(requester, org);
        RoleStrategy strategy = roleStrategyFactory.getStrategy(requester);
        if (!isMember && !strategy.canOverride(requester) && !strategy.canViewAllTasks(requester)) {
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
