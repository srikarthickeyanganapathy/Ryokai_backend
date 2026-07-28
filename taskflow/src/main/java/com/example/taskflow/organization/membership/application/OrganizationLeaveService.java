package com.example.taskflow.organization.membership.application;

import com.example.taskflow.notification.domain.Notification;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.LeaveRequest;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.membership.dto.LeaveRequestDTO;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.LeaveRequestRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.example.taskflow.notification.application.NotificationService;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.organization.rbac.application.PermissionService;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.team.application.TeamService;

@Service
public class OrganizationLeaveService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final TeamService teamService;
    private final PermissionService permissionService;

    public OrganizationLeaveService(OrganizationRepository organizationRepository,
                                    OrganizationMembershipRepository membershipRepository,
                                    LeaveRequestRepository leaveRequestRepository,
                                    TaskRepository taskRepository,
                                    NotificationService notificationService,
                                    TeamService teamService,
                                    PermissionService permissionService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.teamService = teamService;
        this.permissionService = permissionService;
    }

    @Transactional
    public LeaveRequestDTO requestLeave(Long orgId, User user, String reason) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (!user.isMemberOf(org)) {
            throw new IllegalArgumentException("You are not a member of this organization");
        }

        if (leaveRequestRepository.existsByUserAndOrganizationAndStatus(user, org,
                LeaveRequest.LeaveRequestStatus.PENDING)) {
            throw new IllegalStateException("You already have a pending leave request for this organization.");
        }

        LeaveRequest request = new LeaveRequest();
        request.setUser(user);
        request.setOrganization(org);
        request.setReason(reason);
        request.setStatus(LeaveRequest.LeaveRequestStatus.PENDING);
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify all org admins about the leave request
        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);
        for (OrganizationMembership m : members) {
            if (m.getOrgRole().isBuiltinAdmin()) {
                notificationService.createAndSend(m.getUser(), user,
                        com.example.taskflow.notification.event.NotificationEvent.LEAVE_REQUESTED,
                        "Leave Request: " , user.getUsername() + " has requested to leave " + org.getName(), null ,
                        "leave-request:" + saved.getId(), user);
            }
        }

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO approveLeave(Long orgId, Long requestId, User adminUser) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        permissionService.requireAuthorization(adminUser, com.example.taskflow.security.PermissionCode.LEAVE_APPROVE, orgId);

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Leave request does not belong to this organization");
        }

        if (request.getStatus() != LeaveRequest.LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("This leave request has already been " + request.getStatus());
        }

        User leavingUser = request.getUser();
        if (adminUser.getId().equals(leavingUser.getId())) {
            throw new UnauthorizedActionException(
                    "You cannot approve your own leave request. Another Admin must approve it.");
        }

        org.ensureNotLastAdmin(leavingUser);

        boolean hasPendingTasks = taskRepository.findByAssignee(leavingUser).stream()
                .anyMatch(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId) &&
                               !t.getCurrentStatus().isTerminal());
        
        if (hasPendingTasks) {
            throw new IllegalStateException("Cannot approve leave request because the user has pending tasks. Please reassign their tasks first.");
        }

        teamService.removeUserFromAllTeams(leavingUser, orgId);

        membershipRepository.findByUserAndOrganization(leavingUser, org)
                .ifPresent(membershipRepository::delete);

        request.setStatus(LeaveRequest.LeaveRequestStatus.APPROVED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        LeaveRequest saved = leaveRequestRepository.save(request);

        notificationService.createAndSend(leavingUser, adminUser,
                com.example.taskflow.notification.event.NotificationEvent.LEAVE_APPROVED,
                "Leave Approved", "Your leave request for " + org.getName() + " has been approved.",
                null, "leave-approved:" + saved.getId(), adminUser);

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO rejectLeave(Long orgId, Long requestId, User adminUser, String adminComment) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        permissionService.requireAuthorization(adminUser, com.example.taskflow.security.PermissionCode.LEAVE_APPROVE, orgId);

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Leave request does not belong to this organization");
        }

        if (request.getStatus() != LeaveRequest.LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("This leave request has already been " + request.getStatus());
        }

        request.setStatus(LeaveRequest.LeaveRequestStatus.REJECTED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminComment(adminComment);
        LeaveRequest saved = leaveRequestRepository.save(request);

        notificationService.createAndSend(request.getUser(), adminUser,
                com.example.taskflow.notification.event.NotificationEvent.LEAVE_REJECTED,
                "Leave Rejected", "Your leave request for " + org.getName() + " has been rejected."
                        + (adminComment != null ? " Reason: " + adminComment : ""),
                null, "leave-rejected:" + saved.getId(), adminUser);

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listLeaveRequests(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (!user.isMemberOf(org)) {
            throw new UnauthorizedActionException("You are not a member of this organization");
        }

        if (permissionService.isAuthorized(user, com.example.taskflow.security.PermissionCode.LEAVE_APPROVE, orgId)) {
            return leaveRequestRepository.findByOrganizationId(orgId).stream()
                    .map(this::mapToLeaveRequestDTO)
                    .collect(Collectors.toList());
        }

        return leaveRequestRepository.findByOrganizationId(orgId).stream()
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .map(this::mapToLeaveRequestDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LeaveRequestDTO getLeaveRequestStatus(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        return leaveRequestRepository
                .findByUserAndOrganizationAndStatus(user, org, LeaveRequest.LeaveRequestStatus.PENDING)
                .map(this::mapToLeaveRequestDTO)
                .orElse(null);
    }

    private LeaveRequestDTO mapToLeaveRequestDTO(LeaveRequest request) {
        return new LeaveRequestDTO(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getUsername(),
                request.getOrganization().getId(),
                request.getOrganization().getName(),
                request.getReason(),
                request.getStatus().name(),
                request.getAdminComment(),
                request.getReviewedBy() != null ? request.getReviewedBy().getUsername() : null,
                request.getCreatedAt(),
                request.getReviewedAt());
    }
}