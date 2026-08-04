package com.example.taskflow.organization.membership.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.notification.application.NotificationService;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.domain.LeaveRequest;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.dto.CreateLeaveRequestDTO;
import com.example.taskflow.organization.membership.dto.LeaveRequestDTO;
import com.example.taskflow.organization.membership.infrastructure.persistence.LeaveRequestRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.user.domain.User;

@Service
public class LeaveRequestService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final NotificationService notificationService;
    private final AuthorizationEngine authorizationEngine;

    public LeaveRequestService(OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository membershipRepository,
                               LeaveRequestRepository leaveRequestRepository,
                               NotificationService notificationService,
                               AuthorizationEngine authorizationEngine) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.notificationService = notificationService;
        this.authorizationEngine = authorizationEngine;
    }

    private int countWorkingDays(LocalDate start, LocalDate end) {
        int workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        return workingDays;
    }

    private int countCalendarDays(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }

    @Transactional
    public LeaveRequestDTO requestLeave(Long orgId, User user, CreateLeaveRequestDTO dto) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (!user.isMemberOf(org)) {
            throw new IllegalArgumentException("You are not a member of this organization");
        }

        if (leaveRequestRepository.existsByUserAndOrganizationAndStatus(user, org, LeaveRequest.LeaveRequestStatus.PENDING)) {
            throw new IllegalStateException("You already have a pending workforce leave request for this organization.");
        }

        LocalDate startDate = (dto != null && dto.getStartDate() != null) ? dto.getStartDate() : LocalDate.now();
        LocalDate endDate = (dto != null && dto.getEndDate() != null) ? dto.getEndDate() : startDate;

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be earlier than start date");
        }

        int calendarDays = countCalendarDays(startDate, endDate);
        int workingDays = countWorkingDays(startDate, endDate);
        if (dto != null && Boolean.TRUE.equals(dto.getIsHalfDay())) {
            workingDays = Math.max(1, workingDays); // Half-day counted as 1 entry or fraction if float, keeping int accurate
        }

        LeaveRequest request = new LeaveRequest();
        request.setUser(user);
        request.setOrganization(org);
        request.setLeaveType(dto != null && dto.getLeaveType() != null ? dto.getLeaveType() : "VACATION");
        request.setReason(dto != null ? dto.getReason() : null);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setWorkingDays(workingDays);
        request.setCalendarDays(calendarDays);
        request.setIsHalfDay(dto != null && Boolean.TRUE.equals(dto.getIsHalfDay()));
        request.setIsEmergency(dto != null && Boolean.TRUE.equals(dto.getIsEmergency()));
        request.setAttachmentUrl(dto != null ? dto.getAttachmentUrl() : null);
        request.setStatus(LeaveRequest.LeaveRequestStatus.PENDING);

        LeaveRequest saved = leaveRequestRepository.save(request);

        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);
        for (OrganizationMembership m : members) {
            if (m.getOrgRole() != null && "ADMIN".equals(m.getOrgRole().getName())) {
                notificationService.createAndSend(m.getUser(), user,
                        NotificationEvent.LEAVE_REQUESTED,
                        "Leave Request: " + user.getUsername(),
                        user.getUsername() + " has requested " + saved.getLeaveType() + " time off (" + workingDays + " working days) in " + org.getName(),
                        null, "leave-request:" + saved.getId(), user);
            }
        }

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO approveLeave(Long orgId, Long requestId, User adminUser) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Leave request does not belong to this organization");
        }

        if (request.getStatus() != LeaveRequest.LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("This leave request has already been " + request.getStatus());
        }

        User employee = request.getUser();
        if (adminUser.getId().equals(employee.getId()) && !adminUser.isSuperAdmin()) {
            throw new UnauthorizedActionException("You cannot approve your own leave request. Another Admin must approve it.");
        }

        request.setStatus(LeaveRequest.LeaveRequestStatus.APPROVED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        LeaveRequest saved = leaveRequestRepository.save(request);

        notificationService.createAndSend(employee, adminUser,
                NotificationEvent.LEAVE_APPROVED,
                "Leave Approved",
                "Your " + request.getLeaveType() + " request for " + org.getName() + " has been approved (" + request.getStartDate() + " to " + request.getEndDate() + ").",
                null, "leave-approved:" + saved.getId(), adminUser);

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO rejectLeave(Long orgId, Long requestId, User adminUser, String adminComment) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

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
                NotificationEvent.LEAVE_REJECTED,
                "Leave Rejected",
                "Your leave request for " + org.getName() + " has been rejected." + (adminComment != null ? " Reason: " + adminComment : ""),
                null, "leave-rejected:" + saved.getId(), adminUser);

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO cancelLeave(Long orgId, Long requestId, User user) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Leave request does not belong to this organization");
        }

        if (!request.getUser().getId().equals(user.getId()) && !user.isSuperAdmin()) {
            throw new UnauthorizedActionException("You are not authorized to cancel this leave request");
        }

        if (request.getStatus() != LeaveRequest.LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("Cannot cancel leave request because it is already in status: " + request.getStatus());
        }

        request.setStatus(LeaveRequest.LeaveRequestStatus.CANCELLED);
        LeaveRequest saved = leaveRequestRepository.save(request);

        List<OrganizationMembership> members = membershipRepository.findByOrganizationId(orgId);
        for (OrganizationMembership m : members) {
            if (m.getOrgRole() != null && "ADMIN".equals(m.getOrgRole().getName())) {
                notificationService.createAndSend(m.getUser(), user,
                        NotificationEvent.LEAVE_CANCELLED,
                        "Leave Request Cancelled",
                        user.getUsername() + " has cancelled their leave request.",
                        null, "leave-cancelled:" + saved.getId(), user);
            }
        }

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listLeaveRequests(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (!user.isMemberOf(org)) {
            throw new UnauthorizedActionException("You are not a member of this organization");
        }

        boolean canManage = authorizationEngine.authorize(
                AuthorizationRequest.builder(user, PermissionCode.LEAVE_APPROVE)
                        .context(Map.of("organizationId", orgId))
                        .requiredScope(ScopeType.ORGANIZATION)
                        .build()).isGranted();

        if (canManage) {
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

    public LeaveRequestDTO mapToLeaveRequestDTO(LeaveRequest request) {
        return new LeaveRequestDTO(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getUsername(),
                request.getOrganization().getId(),
                request.getOrganization().getName(),
                request.getLeaveType(),
                request.getReason(),
                request.getStartDate(),
                request.getEndDate(),
                request.getWorkingDays(),
                request.getCalendarDays(),
                request.getIsHalfDay(),
                request.getIsEmergency(),
                request.getAttachmentUrl(),
                request.getStatus().name(),
                request.getAdminComment(),
                request.getReviewedBy() != null ? request.getReviewedBy().getUsername() : null,
                request.getCreatedAt(),
                request.getReviewedAt());
    }
}
