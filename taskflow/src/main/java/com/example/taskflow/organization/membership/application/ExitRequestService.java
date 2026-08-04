package com.example.taskflow.organization.membership.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.notification.application.NotificationService;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.domain.ExitRequest;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.dto.CreateExitRequestDTO;
import com.example.taskflow.organization.membership.dto.ExitBlockersDTO;
import com.example.taskflow.organization.membership.dto.ExitRequestDTO;
import com.example.taskflow.organization.membership.infrastructure.persistence.ExitRequestRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.team.application.TeamService;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import com.example.taskflow.user.domain.User;

@Service
public class ExitRequestService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final ExitRequestRepository exitRequestRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final NotificationService notificationService;
    private final TeamService teamService;
    private final AuthorizationEngine authorizationEngine;

    public ExitRequestService(OrganizationRepository organizationRepository,
                              OrganizationMembershipRepository membershipRepository,
                              ExitRequestRepository exitRequestRepository,
                              TaskRepository taskRepository,
                              ProjectRepository projectRepository,
                              TeamRepository teamRepository,
                              NotificationService notificationService,
                              TeamService teamService,
                              AuthorizationEngine authorizationEngine) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.exitRequestRepository = exitRequestRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.notificationService = notificationService;
        this.teamService = teamService;
        this.authorizationEngine = authorizationEngine;
    }

    @Transactional(readOnly = true)
    public ExitBlockersDTO getExitBlockers(Long orgId, User user) {
        List<Task> openTasks = taskRepository.findByAssignee(user).stream()
                .filter(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId) && !t.getCurrentStatus().isTerminal())
                .collect(Collectors.toList());

        List<Project> ownedProjects = projectRepository.findOrganizationProjects(orgId).stream()
                .filter(p -> (p.getOwnerUser() != null && p.getOwnerUser().getId().equals(user.getId())) ||
                             (p.getOwnerUser() == null && p.getCreatedBy() != null && p.getCreatedBy().getId().equals(user.getId())))
                .collect(Collectors.toList());

        List<Team> ledTeams = teamRepository.findByOrganizationId(orgId).stream()
                .filter(t -> t.getCreatedBy() != null && t.getCreatedBy().getId().equals(user.getId()))
                .collect(Collectors.toList());

        List<String> details = new ArrayList<>();
        openTasks.forEach(t -> details.add("Assigned to active task: " + t.getTitle() + " (" + t.getCurrentStatus() + ")"));
        ownedProjects.forEach(p -> details.add("Owner of project: " + p.getName()));
        ledTeams.forEach(t -> details.add("Lead of team: " + t.getName()));

        ExitBlockersDTO dto = new ExitBlockersDTO();
        dto.setOpenTasksCount(openTasks.size());
        dto.setOwnedProjectsCount(ownedProjects.size());
        dto.setTeamLeadCount(ledTeams.size());
        dto.setDetails(details);
        dto.setCanSubmit(openTasks.isEmpty());
        return dto;
    }

    @Transactional
    public ExitRequestDTO requestExit(Long orgId, User user, CreateExitRequestDTO createDto) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (!user.isMemberOf(org)) {
            throw new IllegalArgumentException("You are not a member of this organization");
        }

        if (exitRequestRepository.existsByUserAndOrganizationAndStatus(user, org, ExitRequest.ExitRequestStatus.PENDING) ||
            exitRequestRepository.existsByUserAndOrganizationAndStatus(user, org, ExitRequest.ExitRequestStatus.OFFBOARDING)) {
            throw new IllegalStateException("You already have an active exit request for this organization.");
        }

        ExitBlockersDTO blockers = getExitBlockers(orgId, user);
        if (!blockers.isCanSubmit()) {
            throw new IllegalStateException("Cannot submit exit request while having open tasks assigned to you. Please complete or reassign your tasks first.");
        }

        ExitRequest request = new ExitRequest();
        request.setUser(user);
        request.setOrganization(org);
        request.setReason(createDto != null ? createDto.getReason() : null);
        request.setStatus(ExitRequest.ExitRequestStatus.PENDING);
        ExitRequest saved = exitRequestRepository.save(request);

        // Notify admins
        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);
        for (OrganizationMembership m : members) {
            if (m.getOrgRole() != null && "ADMIN".equals(m.getOrgRole().getName())) {
                notificationService.createAndSend(m.getUser(), user,
                        NotificationEvent.EXIT_REQUESTED,
                        "Exit Request: " + user.getUsername(),
                        user.getUsername() + " has requested to depart from organization " + org.getName(),
                        null, "exit-request:" + saved.getId(), user);
            }
        }

        return mapToExitRequestDTO(saved);
    }

    @Transactional
    public ExitRequestDTO approveExit(Long orgId, Long requestId, User adminUser, boolean triggerOffboarding) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        ExitRequest request = exitRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Exit request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Exit request does not belong to this organization");
        }

        if (request.getStatus() != ExitRequest.ExitRequestStatus.PENDING && request.getStatus() != ExitRequest.ExitRequestStatus.OFFBOARDING) {
            throw new IllegalStateException("This exit request cannot be processed as its current status is " + request.getStatus());
        }

        User leavingUser = request.getUser();
        if (adminUser.getId().equals(leavingUser.getId())) {
            throw new UnauthorizedActionException("You cannot approve your own exit request. Another Admin must review it.");
        }

        org.ensureNotLastAdmin(leavingUser);

        boolean hasOpenTasks = taskRepository.findByAssignee(leavingUser).stream()
                .anyMatch(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId) && !t.getCurrentStatus().isTerminal());
        if (hasOpenTasks) {
            throw new IllegalStateException("Cannot finish exit request because the member currently has open tasks assigned in this organization.");
        }

        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());

        if (triggerOffboarding && request.getStatus() == ExitRequest.ExitRequestStatus.PENDING) {
            request.setStatus(ExitRequest.ExitRequestStatus.OFFBOARDING);
            ExitRequest saved = exitRequestRepository.save(request);
            notificationService.createAndSend(leavingUser, adminUser,
                    NotificationEvent.EXIT_APPROVED,
                    "Exit Request in Offboarding",
                    "Your exit request for " + org.getName() + " has been approved and moved to the Offboarding phase.",
                    null, "exit-offboarding:" + saved.getId(), adminUser);
            return mapToExitRequestDTO(saved);
        }

        // Complete departure
        teamService.removeUserFromAllTeams(leavingUser, orgId);
        membershipRepository.findByUserAndOrganization(leavingUser, org)
                .ifPresent(membershipRepository::delete);

        request.setStatus(ExitRequest.ExitRequestStatus.COMPLETED);
        request.setEffectiveExitDate(LocalDate.now());
        ExitRequest saved = exitRequestRepository.save(request);

        notificationService.createAndSend(leavingUser, adminUser,
                NotificationEvent.EXIT_APPROVED,
                "Exit Completed",
                "Your departure from organization " + org.getName() + " is completed.",
                null, "exit-completed:" + saved.getId(), adminUser);

        return mapToExitRequestDTO(saved);
    }

    @Transactional
    public ExitRequestDTO rejectExit(Long orgId, Long requestId, User adminUser, String decisionComment) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        ExitRequest request = exitRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Exit request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Exit request does not belong to this organization");
        }

        if (request.getStatus() != ExitRequest.ExitRequestStatus.PENDING && request.getStatus() != ExitRequest.ExitRequestStatus.OFFBOARDING) {
            throw new IllegalStateException("This exit request is already in status " + request.getStatus());
        }

        request.setStatus(ExitRequest.ExitRequestStatus.REJECTED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        request.setDecisionComment(decisionComment);
        ExitRequest saved = exitRequestRepository.save(request);

        notificationService.createAndSend(request.getUser(), adminUser,
                NotificationEvent.EXIT_REJECTED,
                "Exit Request Rejected",
                "Your exit request for " + org.getName() + " was rejected." + (decisionComment != null ? " Note: " + decisionComment : ""),
                null, "exit-rejected:" + saved.getId(), adminUser);

        return mapToExitRequestDTO(saved);
    }

    @Transactional
    public ExitRequestDTO cancelExit(Long orgId, Long requestId, User user) {
        ExitRequest request = exitRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Exit request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Exit request does not belong to this organization");
        }

        if (!request.getUser().getId().equals(user.getId()) && !user.isSuperAdmin()) {
            throw new UnauthorizedActionException("You are not authorized to cancel this exit request");
        }

        if (request.getStatus() != ExitRequest.ExitRequestStatus.PENDING && request.getStatus() != ExitRequest.ExitRequestStatus.OFFBOARDING) {
            throw new IllegalStateException("Cannot cancel request because it is already in status: " + request.getStatus());
        }

        request.setStatus(ExitRequest.ExitRequestStatus.CANCELLED);
        ExitRequest saved = exitRequestRepository.save(request);

        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);
        for (OrganizationMembership m : members) {
            if (m.getOrgRole() != null && "ADMIN".equals(m.getOrgRole().getName())) {
                notificationService.createAndSend(m.getUser(), user,
                        NotificationEvent.EXIT_CANCELLED,
                        "Exit Request Cancelled",
                        user.getUsername() + " has cancelled their exit request.",
                        null, "exit-cancelled:" + saved.getId(), user);
            }
        }

        return mapToExitRequestDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<ExitRequestDTO> listExitRequests(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (!user.isMemberOf(org)) {
            throw new UnauthorizedActionException("You are not a member of this organization");
        }

        boolean canApprove = authorizationEngine.authorize(
                AuthorizationRequest.builder(user, PermissionCode.EXIT_REQUEST_APPROVE)
                        .context(java.util.Map.of("organizationId", orgId))
                        .requiredScope(ScopeType.ORGANIZATION)
                        .build()).isGranted() ||
                authorizationEngine.authorize(
                        AuthorizationRequest.builder(user, PermissionCode.MEMBER_EXIT_APPROVE)
                                .context(java.util.Map.of("organizationId", orgId))
                                .requiredScope(ScopeType.ORGANIZATION)
                                .build()).isGranted();

        if (canApprove) {
            return exitRequestRepository.findByOrganizationId(orgId).stream()
                    .map(this::mapToExitRequestDTO)
                    .collect(Collectors.toList());
        }

        return exitRequestRepository.findByOrganizationId(orgId).stream()
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .map(this::mapToExitRequestDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExitRequestDTO getExitRequestStatus(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        return exitRequestRepository
                .findByUserAndOrganizationAndStatusIn(user, org, List.of(ExitRequest.ExitRequestStatus.PENDING, ExitRequest.ExitRequestStatus.OFFBOARDING)).stream()
                .findFirst()
                .map(this::mapToExitRequestDTO)
                .orElse(null);
    }

    public ExitRequestDTO mapToExitRequestDTO(ExitRequest request) {
        return new ExitRequestDTO(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getUsername(),
                request.getOrganization().getId(),
                request.getOrganization().getName(),
                request.getReason(),
                request.getStatus().name(),
                request.getDecisionComment(),
                request.getReviewedBy() != null ? request.getReviewedBy().getUsername() : null,
                request.getRequestedAt(),
                request.getReviewedAt(),
                request.getEffectiveExitDate());
    }
}
