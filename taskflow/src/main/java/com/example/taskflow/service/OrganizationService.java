package com.example.taskflow.service;

import com.example.taskflow.domain.LeaveRequest;
import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.OrgRole;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.LeaveRequestDTO;
import com.example.taskflow.dto.MembershipResponseDTO;
import com.example.taskflow.dto.OrganizationResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.exception.UserNotFoundException;
import com.example.taskflow.repository.LeaveRequestRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TeamRepository;
import com.example.taskflow.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final NotificationService notificationService;

    public OrganizationService(OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            TeamRepository teamRepository,
            NotificationService notificationService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.teamRepository = teamRepository;
        this.notificationService = notificationService;
    }

    // ========================================================================
    // HELPERS — Super Admin detection & membership validation
    // ========================================================================

    private boolean isSuperAdmin(User user) {
        if (user == null || user.getRoles() == null)
            return false;
        return user.getRoles().stream()
                .anyMatch(r -> {
                    String name = r.getName();
                    if (name.startsWith("ROLE_"))
                        name = name.substring(5);
                    return "SUPER_ADMIN".equals(name);
                });
    }

    private void validateMembershipOrSuperAdmin(User user, Organization org) {
        if (isSuperAdmin(user))
            return;
        membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
    }

    private OrganizationMembership requireAdminMembership(User user, Organization org) {
        if (isSuperAdmin(user))
            return null; // Super Admin bypasses
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (membership.getOrgRole() != OrgRole.ADMIN) {
            throw new UnauthorizedActionException("Only the Organization Admin can perform this action");
        }
        return membership;
    }

    private void ensureNotLastAdmin(Organization org, User userBeingRemoved) {
        long adminCount = membershipRepository.findByOrganizationId(org.getId()).stream()
                .filter(m -> m.getOrgRole() == OrgRole.ADMIN)
                .count();
        boolean isAdmin = membershipRepository.findByUserAndOrganization(userBeingRemoved, org)
                .map(m -> m.getOrgRole() == OrgRole.ADMIN)
                .orElse(false);
        if (isAdmin && adminCount <= 1) {
            throw new IllegalStateException(
                    "Cannot remove the last Admin of the organization. Promote another member to Admin first.");
        }
    }

    private void removeUserFromOrgTeams(User user, Long orgId) {
        teamRepository.findByOrganizationId(orgId).forEach(team -> {
            if (team.getMembers().remove(user)) {
                teamRepository.save(team);
            }
        });
    }

    // ========================================================================
    // ONE-ORG RULE: A user can belong to only ONE organization at a time
    // ========================================================================

    @Transactional
    public OrganizationResponseDTO createOrganization(String name, String description, User adminUser) {
        if (!membershipRepository.findByUserId(adminUser.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "You are already a member of an organization. You must leave your current organization before creating a new one.");
        }

        Organization org = new Organization();
        org.setName(name);
        org.setDescription(description);
        org.setCreatedBy(adminUser);
        Organization saved = organizationRepository.save(org);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(adminUser);
        membership.setOrganization(saved);
        membership.setOrgRole(OrgRole.ADMIN);
        membershipRepository.save(membership);

        return mapToResponseDTO(saved);
    }

    @Transactional
    public MembershipResponseDTO inviteMember(Long orgId, Long userId, OrgRole orgRole, User invitedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Only the organization ADMIN can invite members
        OrganizationMembership inviterMembership = membershipRepository.findByUserAndOrganization(invitedBy, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (inviterMembership.getOrgRole() != OrgRole.ADMIN) {
            throw new UnauthorizedActionException("Only the Organization Admin can invite members");
        }

        // Cannot invite if user is already in ANY organization
        if (!membershipRepository.findByUserId(user.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "User " + user.getUsername() + " is already a member of another organization. " +
                            "They must leave their current organization first.");
        }

        if (membershipRepository.existsByUserAndOrganization(user, org)) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(user);
        membership.setOrganization(org);
        membership.setOrgRole(orgRole);
        OrganizationMembership saved = membershipRepository.save(membership);

        return mapToMembershipDTO(saved);
    }

    // ========================================================================
    // LEAVE REQUEST SYSTEM (Admin-gated)
    // ========================================================================

    @Transactional
    public LeaveRequestDTO requestLeave(Long orgId, User user, String reason) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this organization"));

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
            if (m.getOrgRole() == OrgRole.ADMIN) {
                notificationService.createAndSend(m.getUser(), user,
                        com.example.taskflow.notification.NotificationEvent.LEAVE_REQUESTED,
                        "Leave Request: " , user.getUsername() + " has requested to leave " + org.getName(), null ,
                        "leave-request:" + saved.getId());
            }
        }

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO approveLeave(Long orgId, Long requestId, User adminUser) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Validate admin is the org ADMIN
        OrganizationMembership adminMembership = membershipRepository.findByUserAndOrganization(adminUser, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (adminMembership.getOrgRole() != OrgRole.ADMIN) {
            throw new UnauthorizedActionException("Only the Organization Admin can approve leave requests");
        }

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Leave request does not belong to this organization");
        }

        if (request.getStatus() != LeaveRequest.LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("This leave request has already been " + request.getStatus());
        }

        // Fix #11: Block self-approval — admin cannot approve their own leave request
        User leavingUser = request.getUser();
        if (adminUser.getId().equals(leavingUser.getId())) {
            throw new UnauthorizedActionException(
                    "You cannot approve your own leave request. Another Admin must approve it.");
        }

        // Fix #12: Last-admin guard
        ensureNotLastAdmin(org, leavingUser);

        // Fix #9: Reassign ONLY non-completed tasks to the org admin
        taskRepository.findByAssignedTo(leavingUser).stream()
                .filter(t -> t.getOrganization() != null && t.getOrganization().getId().equals(orgId))
                .filter(t -> t.getCurrentStatus() != com.example.taskflow.domain.TaskStatus.APPROVED
                        && t.getCurrentStatus() != com.example.taskflow.domain.TaskStatus.COMPLETED)
                .forEach(task -> {
                    task.setAssignedTo(adminUser);
                    task.setCurrentStatus(com.example.taskflow.domain.TaskStatus.ASSIGNED);
                    taskRepository.save(task);
                });

        // Fix #8: Remove from all teams within this org
        removeUserFromOrgTeams(leavingUser, orgId);

        // Remove membership
        membershipRepository.findByUserAndOrganization(leavingUser, org)
                .ifPresent(membershipRepository::delete);

        // Mark leave as approved
        request.setStatus(LeaveRequest.LeaveRequestStatus.APPROVED);
        request.setReviewedBy(adminUser);
        request.setReviewedAt(LocalDateTime.now());
        LeaveRequest saved = leaveRequestRepository.save(request);

        // Notify the leaving user
        notificationService.createAndSend(leavingUser, adminUser,
                com.example.taskflow.notification.NotificationEvent.LEAVE_APPROVED,
                "Leave Approved", "Your leave request for " + org.getName() + " has been approved.",
                null, "leave-approved:" + saved.getId());

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO rejectLeave(Long orgId, Long requestId, User adminUser, String adminComment) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        OrganizationMembership adminMembership = membershipRepository.findByUserAndOrganization(adminUser, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (adminMembership.getOrgRole() != OrgRole.ADMIN) {
            throw new UnauthorizedActionException("Only the Organization Admin can reject leave requests");
        }

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

        // Notify the user their leave was rejected
        notificationService.createAndSend(request.getUser(), adminUser,
                com.example.taskflow.notification.NotificationEvent.LEAVE_REJECTED,
                "Leave Rejected", "Your leave request for " + org.getName() + " has been rejected."
                        + (adminComment != null ? " Reason: " + adminComment : ""),
                null, "leave-rejected:" + saved.getId());

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listLeaveRequests(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));

        if (membership.getOrgRole() == OrgRole.ADMIN) {
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

    // ========================================================================
    // MEMBER MANAGEMENT (Org Admin only)
    // ========================================================================

    @Transactional
    public void removeMember(Long orgId, Long userId, User removedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Only ADMIN can remove members
        OrganizationMembership removerMembership = membershipRepository.findByUserAndOrganization(removedBy, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (removerMembership.getOrgRole() != OrgRole.ADMIN) {
            throw new UnauthorizedActionException("Only the Organization Admin can remove members");
        }

        // Fix #10: Block self-removal — admin must use the leave request workflow
        if (removedBy.getId().equals(userId)) {
            throw new UnauthorizedActionException(
                    "Admins cannot remove themselves directly. Use the leave request workflow instead.");
        }

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization"));

        // Fix #12: Last-admin guard
        ensureNotLastAdmin(org, user);

        // Reassign tasks to the admin before removing
        taskRepository.findByAssignedTo(user).stream()
                .filter(t -> t.getOrganization() != null && t.getOrganization().getId().equals(orgId))
                .filter(t -> t.getCurrentStatus() != com.example.taskflow.domain.TaskStatus.APPROVED
                          && t.getCurrentStatus() != com.example.taskflow.domain.TaskStatus.COMPLETED)
                .forEach(task -> {
                    task.setAssignedTo(removedBy);
                    task.setCurrentStatus(com.example.taskflow.domain.TaskStatus.ASSIGNED);
                    taskRepository.save(task);
                });

        // Fix #8: Remove from all teams within this org
        removeUserFromOrgTeams(user, orgId);

        membershipRepository.delete(membership);
    }

    @Transactional
    public MembershipResponseDTO updateMemberRole(Long orgId, Long userId, OrgRole newRole, User callerUser) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Fix #1: Only org ADMIN can change roles
        requireAdminMembership(callerUser, org);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization"));

        // Prevent demoting yourself if you're the last admin
        if (callerUser.getId().equals(userId) && membership.getOrgRole() == OrgRole.ADMIN && newRole != OrgRole.ADMIN) {
            ensureNotLastAdmin(org, user);
        }

        membership.setOrgRole(newRole);
        OrganizationMembership saved = membershipRepository.save(membership);

        return mapToMembershipDTO(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationResponseDTO getOrganization(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        // Fix #6: Validate caller is a member or Super Admin
        validateMembershipOrSuperAdmin(caller, org);
        return mapToResponseDTO(org);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO> listUserOrganizations(Long userId) {
        return membershipRepository.findByUserId(userId).stream()
                .map(m -> mapToResponseDTO(m.getOrganization()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrganizationResponseDTO getUserOrganization(Long userId) {
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty())
            return null;
        return mapToResponseDTO(memberships.get(0).getOrganization());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponseDTO> listOrganizationMembers(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        // Fix #6: Validate caller is a member or Super Admin
        validateMembershipOrSuperAdmin(caller, org);
        return membershipRepository.findByOrganizationId(orgId).stream()
                .map(this::mapToMembershipDTO)
                .collect(Collectors.toList());
    }

    // ========================================================================
    // DTO MAPPING
    // ========================================================================

    private OrganizationResponseDTO mapToResponseDTO(Organization org) {
        int memberCount = (int) membershipRepository.countByOrganizationId(org.getId());
        return new OrganizationResponseDTO(
                org.getId(),
                org.getName(),
                org.getDescription(),
                org.getCreatedBy() != null ? org.getCreatedBy().getUsername() : null,
                org.getCreatedAt(),
                memberCount);
    }

    private MembershipResponseDTO mapToMembershipDTO(OrganizationMembership membership) {
        return new MembershipResponseDTO(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getUsername(),
                membership.getOrgRole(),
                membership.getJoinedAt());
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
