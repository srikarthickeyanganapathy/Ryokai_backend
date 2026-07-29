package com.example.taskflow.organization.core.application;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.LeaveRequest;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.user.exception.UserNotFoundException;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.LeaveRequestRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RoleRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.example.taskflow.audit.application.AuditService;
import com.example.taskflow.task.application.orchestration.TaskAuditService;
import com.example.taskflow.team.application.TeamService;

@Service
public class OrganizationLifecycleService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final com.example.taskflow.project.infrastructure.persistence.ProjectRepository projectRepository;
    private final com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationInviteRepository inviteRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final RoleRepository roleRepository;
    private final TaskAuditService taskAuditService;
    private final AuditService auditService;
    private final TeamService teamService;

    public OrganizationLifecycleService(OrganizationRepository organizationRepository,
                                        OrganizationMembershipRepository membershipRepository,
                                        UserRepository userRepository,
                                        TaskRepository taskRepository,
                                        com.example.taskflow.project.infrastructure.persistence.ProjectRepository projectRepository,
                                        com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationInviteRepository inviteRepository,
                                        LeaveRequestRepository leaveRequestRepository,
                                        RoleRepository roleRepository,
                                        TaskAuditService taskAuditService,
                                        AuditService auditService,
                                        TeamService teamService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.inviteRepository = inviteRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.roleRepository = roleRepository;
        this.taskAuditService = taskAuditService;
        this.auditService = auditService;
        this.teamService = teamService;
    }

    @Transactional
    public void leaveOrDissolve(Long orgId, User adminUser, Long successorUserId, boolean dissolve) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        org.requireActive();

        OrganizationMembership adminMembership = membershipRepository.findByUserAndOrganization(adminUser, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (adminMembership.getOrgRole() == null || !"ADMIN".equals(adminMembership.getOrgRole().getName())) {
            throw new UnauthorizedActionException("Only the Organization Admin can perform this action");
        }

        List<OrganizationMembership> allMemberships = membershipRepository.findByOrganizationId(orgId);
        boolean isAlone = allMemberships.size() <= 1;

        if (isAlone || dissolve) {
            dissolveOrganization(org, adminUser);
        } else {
            transferOwnership(org, adminUser, adminMembership, successorUserId);
        }
    }

    private void dissolveOrganization(Organization org, User adminUser) {
        Long orgId = org.getId();

        List<Task> orgTasks = taskRepository.findByOrgId(orgId);
        taskRepository.deleteAll(orgTasks);

        List<com.example.taskflow.project.domain.Project> orgProjects = projectRepository.findByOrganizationId(orgId);
        projectRepository.deleteAll(orgProjects);

        List<com.example.taskflow.organization.membership.domain.OrganizationInvite> invites = inviteRepository.findByOrganizationId(orgId);
        inviteRepository.deleteAll(invites);

        List<LeaveRequest> leaveRequests = leaveRequestRepository.findByOrganizationId(orgId);
        leaveRequestRepository.deleteAll(leaveRequests);

        organizationRepository.delete(org);
        
        auditService.record("ORG_DISSOLVED", adminUser, "ORGANIZATION", orgId,
                null, null, "Dissolved organization: " + org.getName());
    }

    private void transferOwnership(Organization org, User adminUser, OrganizationMembership adminMembership, Long successorUserId) {
        Long orgId = org.getId();

        if (successorUserId == null) {
            throw new IllegalArgumentException("A successor must be specified to transfer ownership of the organization.");
        }

        User successor = userRepository.findById(successorUserId)
                .orElseThrow(() -> new UserNotFoundException("Successor user not found: " + successorUserId));

        OrganizationMembership successorMembership = membershipRepository.findByUserAndOrganization(successor, org)
                .orElseThrow(() -> new IllegalArgumentException("Successor is not a member of this organization."));

        Role adminRole = roleRepository.findByNameAndOrganizationId("ADMIN", orgId)
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found in organization"));

        successorMembership.setOrgRole(adminRole);
        membershipRepository.save(successorMembership);

        taskRepository.findByAssignee(adminUser).stream()
                .filter(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId))
                .filter(t -> !t.getCurrentStatus().isTerminal())
                .forEach(task -> {
                    String oldStatus = task.getCurrentStatus().name();
                    task.setAssignee(successor);
                    task.setCurrentStatus(com.example.taskflow.task.domain.model.TaskStatus.IN_PROGRESS);
                    Task updated = taskRepository.save(task);
                    taskAuditService.recordStatus(updated, oldStatus, "IN_PROGRESS", "REASSIGNED", adminUser, "Reassigned due to admin transfer");
                });

        teamService.removeUserFromAllTeams(adminUser, orgId);

        membershipRepository.delete(adminMembership);

        auditService.recordSync("ORG_ADMIN_TRANSFERRED", adminUser, "ORGANIZATION", orgId,
                adminUser.getUsername(), successor.getUsername(),
                "Transferred admin role to " + successor.getUsername() + " and left organization");
    }
}