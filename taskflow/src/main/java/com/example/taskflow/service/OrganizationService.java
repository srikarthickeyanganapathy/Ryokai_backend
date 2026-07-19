package com.example.taskflow.service;

import com.example.taskflow.domain.LeaveRequest;
import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.LeaveRequestDTO;
import com.example.taskflow.dto.MembershipResponseDTO;
import com.example.taskflow.dto.OrganizationResponseDTO;
import com.example.taskflow.exception.OrganizationSuspendedException;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.exception.UserNotFoundException;
import com.example.taskflow.repository.LeaveRequestRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.RoleRepository;
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
    private final TaskAuditService taskAuditService;
    private final AuditService auditService;
    private final RoleRepository roleRepository;
    private final com.example.taskflow.repository.PermissionRepository permissionRepository;
    private final RoleService roleService;
    private final com.example.taskflow.repository.TeamMemberRepository teamMemberRepository;
    private final com.example.taskflow.repository.ProjectRepository projectRepository;
    private final com.example.taskflow.repository.OrganizationInviteRepository inviteRepository;
    private final PermissionService permissionService;

    public OrganizationService(OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            LeaveRequestRepository leaveRequestRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            TeamRepository teamRepository,
            NotificationService notificationService,
            TaskAuditService taskAuditService,
            AuditService auditService,
            RoleRepository roleRepository,
                       com.example.taskflow.repository.PermissionRepository permissionRepository,
            RoleService roleService,
            com.example.taskflow.repository.TeamMemberRepository teamMemberRepository,
            com.example.taskflow.repository.ProjectRepository projectRepository,
            com.example.taskflow.repository.OrganizationInviteRepository inviteRepository,
            PermissionService permissionService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.teamRepository = teamRepository;
        this.notificationService = notificationService;
        this.taskAuditService = taskAuditService;
        this.auditService = auditService;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleService = roleService;
        this.teamMemberRepository = teamMemberRepository;
        this.projectRepository = projectRepository;
        this.inviteRepository = inviteRepository;
        this.permissionService = permissionService;
    }

    // ========================================================================
    // HELPERS  -  Super Admin detection & membership validation
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

    private OrganizationMembership requirePermission(User user, Organization org, String permission) {
        if (isSuperAdmin(user))
            return null; // Super Admin bypasses
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (membership.getOrgRole() == null || membership.getOrgRole().getPermissions().stream().noneMatch(p -> p.getName().equals(permission))) {
            throw new UnauthorizedActionException("This action requires the " + permission + " permission.");
        }
        return membership;
    }

    /**
     * Rejects the operation if the organization is not ACTIVE (i.e. SUSPENDED or DELETED).
     * This guards direct-call paths that are not covered by CustomPermissionEvaluator.
     */
    private void requireActiveOrganization(Organization org) {
        if (org.getStatus() != Organization.OrgStatus.ACTIVE) {
            throw new OrganizationSuspendedException(
                    "Organization '" + org.getName() + "' is " + org.getStatus().name().toLowerCase()
                    + ". All operations are restricted until it is reactivated.");
        }
    }

    private void ensureNotLastAdmin(Organization org, User userBeingRemoved) {
        long adminCount = membershipRepository.findByOrganizationId(org.getId()).stream()
                .filter(m -> m.getOrgRole() != null && m.getOrgRole().isBuiltinAdmin())
                .count();
        boolean isAdmin = membershipRepository.findByUserAndOrganization(userBeingRemoved, org)
                .map(m -> m.getOrgRole() != null && m.getOrgRole().isBuiltinAdmin())
                .orElse(false);
        if (isAdmin && adminCount <= 1) {
            throw new IllegalStateException(
                    "Cannot remove the last Admin of the organization. Promote another member to Admin first.");
        }
    }

    private void removeUserFromOrgTeams(User user, Long orgId) {
        teamRepository.findByOrganizationId(orgId).forEach(team -> {
            com.example.taskflow.domain.TeamMemberId tmId = new com.example.taskflow.domain.TeamMemberId(team.getId(), user.getId());
            if (teamMemberRepository.existsById(tmId)) {
                teamMemberRepository.deleteById(tmId);
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

        // Seed only the ADMIN role with all permissions for the creator
        java.util.Set<com.example.taskflow.domain.Permission> adminPerms = loadPermissionsByName(
            "TASK_VIEW", "TASK_ASSIGN", "TASK_EDIT", "TASK_DELETE",
            "TASK_REVIEW", "TASK_DEPENDENCY_EDIT",
            "TASK_REASSIGN", "TASK_ARCHIVE", "ROLE_MANAGE",
            "ORG_MEMBER_INVITE", "ORG_MEMBER_REMOVE", "LEAVE_REQUEST_MANAGE",
            "TEAM_CREATE", "TEAM_MANAGE", "PROJECT_CREATE", "PROJECT_MANAGE",
            "TASK_OVERRIDE");

        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Organization Administrator");
        adminRole.setBuiltin(true);
        adminRole.setOrganization(saved);
        adminRole.setPermissions(adminPerms);
        adminRole.setPriority(0);
        roleRepository.save(adminRole);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(adminUser);
        membership.setOrganization(saved);
        membership.setOrgRole(adminRole);
        membershipRepository.save(membership);

        OrganizationResponseDTO responseDTO = mapToResponseDTO(saved);
        auditService.record("ORG_CREATED", adminUser, "ORGANIZATION", saved.getId(),
                null, responseDTO, "Created organization: " + saved.getName());

        return responseDTO;
    }

    @Transactional
    public MembershipResponseDTO inviteMember(Long orgId, Long userId, Long roleId, User invitedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        requireActiveOrganization(org);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Use explicit permission to invite members
        OrganizationMembership inviterMembership = requirePermission(invitedBy, org, "ORG_MEMBER_INVITE");

        // Cannot invite if user is already in ANY organization
        if (!membershipRepository.findByUserId(user.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "User " + user.getUsername() + " is already a member of another organization. " +
                            "They must leave their current organization first.");
        }

        if (membershipRepository.existsByUserAndOrganization(user, org)) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(user);
        membership.setOrganization(org);
        membership.setOrgRole(role);
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
            if (m.getOrgRole().isBuiltinAdmin()) {
                notificationService.createAndSend(m.getUser(), user,
                        com.example.taskflow.notification.NotificationEvent.LEAVE_REQUESTED,
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

        // Use explicit permission
        OrganizationMembership adminMembership = requirePermission(adminUser, org, "LEAVE_REQUEST_MANAGE");

        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));

        if (!request.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Leave request does not belong to this organization");
        }

        if (request.getStatus() != LeaveRequest.LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("This leave request has already been " + request.getStatus());
        }

        // Fix #11: Block self-approval  -  admin cannot approve their own leave request
        User leavingUser = request.getUser();
        if (adminUser.getId().equals(leavingUser.getId())) {
            throw new UnauthorizedActionException(
                    "You cannot approve your own leave request. Another Admin must approve it.");
        }

        // Fix #12: Last-admin guard
        ensureNotLastAdmin(org, leavingUser);

        // Block leave approval if the user has pending (non-terminal) tasks
        // Bug #8 Fix: org tasks terminate at APPROVED, not COMPLETED — use isTerminal()
        boolean hasPendingTasks = taskRepository.findByAssignee(leavingUser).stream()
                .anyMatch(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId) &&
                               !t.getCurrentStatus().isTerminal());
        
        if (hasPendingTasks) {
            throw new IllegalStateException("Cannot approve leave request because the user has pending tasks. Please reassign their tasks first.");
        }

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
                null, "leave-approved:" + saved.getId(), adminUser);

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional
    public LeaveRequestDTO rejectLeave(Long orgId, Long requestId, User adminUser, String adminComment) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Use explicit permission
        OrganizationMembership adminMembership = requirePermission(adminUser, org, "LEAVE_REQUEST_MANAGE");

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
                null, "leave-rejected:" + saved.getId(), adminUser);

        return mapToLeaveRequestDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listLeaveRequests(Long orgId, User user) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));

        if (membership.getOrgRole().getPermissions().stream().anyMatch(p -> p.getName().equals("LEAVE_REQUEST_MANAGE"))) {
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

        // Use explicit permission
        OrganizationMembership removerMembership = requirePermission(removedBy, org, "ORG_MEMBER_REMOVE");

        // Fix #10: Block self-removal  -  admin must use the leave request workflow
        if (removedBy.getId().equals(userId)) {
            throw new UnauthorizedActionException(
                    "Admins cannot remove themselves directly. Use the leave request workflow instead.");
        }

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization"));

        // Fix #12: Last-admin guard
        ensureNotLastAdmin(org, user);

        // Block removal if the user has pending (non-terminal) tasks
        // Bug #8 Fix: org tasks terminate at APPROVED, not COMPLETED — use isTerminal()
        boolean hasPendingTasks = taskRepository.findByAssignee(user).stream()
                .anyMatch(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId) &&
                               !t.getCurrentStatus().isTerminal());
        
        if (hasPendingTasks) {
            throw new IllegalStateException("Cannot remove member because they have pending tasks. Please reassign their tasks first.");
        }

        removeUserFromOrgTeams(user, orgId);

        membershipRepository.delete(membership);
        
        auditService.recordSync("ORG_MEMBER_REMOVED", removedBy, "ORGANIZATION", org.getId(),
                user.getUsername(), null, "Removed member " + user.getUsername() + " from organization");
    }

    @Transactional
    public MembershipResponseDTO updateMemberRole(Long orgId, Long userId, Long newRoleId, User callerUser) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Fix #1: Only org ADMIN can change roles
        requirePermission(callerUser, org, "ROLE_MANAGE");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization"));

        Role newRole = roleRepository.findById(newRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (newRole.isBuiltinAdmin()) {
            throw new IllegalArgumentException("Only one Admin is allowed in the organization. You cannot promote another member to Admin. Use the Transfer Ownership flow instead.");
        }

        // RB-M01 fix: verify the new role actually belongs to this organization.
        // Previously an org admin could pass the ID of a role from ANOTHER org
        // (or a global builtin) and assign it to a member of their own org -
        // leaking foreign permission grants into the local org.
        if (newRole.getOrganization() == null
                || !newRole.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException(
                "Role does not belong to this organization. Cross-org role assignment is not allowed.");
        }

        // Prevent demoting yourself if you're the last admin
        if (callerUser.getId().equals(userId) && membership.getOrgRole().isBuiltinAdmin() && !newRole.isBuiltinAdmin()) {
            ensureNotLastAdmin(org, user);
        }

        String oldRoleName = membership.getOrgRole().getName();
        membership.setOrgRole(newRole);
        OrganizationMembership saved = membershipRepository.save(membership);
        permissionService.invalidateCache(user.getId());

        MembershipResponseDTO responseDTO = mapToMembershipDTO(saved);
        auditService.record("ORG_MEMBER_ROLE_UPDATED", callerUser, "ORGANIZATION", org.getId(),
                java.util.Map.of("user", user.getUsername(), "oldRole", oldRoleName), 
                java.util.Map.of("user", user.getUsername(), "newRole", newRole.getName()), 
                "Updated member role for " + user.getUsername() + " to " + newRole.getName());

        return responseDTO;
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
    // ROLE MANAGEMENT (Org Admin only)
    // ========================================================================

    @Transactional(readOnly = true)
    public List<com.example.taskflow.dto.RoleResponseDTO> listOrganizationRoles(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        validateMembershipOrSuperAdmin(caller, org);
        return roleService.getRolesByOrganizationId(orgId);
    }

    @Transactional
    public com.example.taskflow.dto.RoleResponseDTO createOrganizationRole(Long orgId, com.example.taskflow.dto.RoleCreateRequestDTO request, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        requireActiveOrganization(org);
        requirePermission(caller, org, "ROLE_MANAGE");
        
        com.example.taskflow.dto.RoleCreateRequestDTO orgRequest = new com.example.taskflow.dto.RoleCreateRequestDTO(
            request.name(), request.description(), orgId, request.priority());
            
        return roleService.createRole(orgRequest, caller);
    }

    @Transactional
    public com.example.taskflow.dto.RoleResponseDTO updateOrganizationRole(Long orgId, Long roleId, com.example.taskflow.dto.RoleUpdateRequestDTO request, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        requireActiveOrganization(org);
        requirePermission(caller, org, "ROLE_MANAGE");
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (role.getOrganization() == null || !role.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Role does not belong to this organization");
        }
        
        return roleService.updateRole(roleId, request, caller);
    }

    @Transactional
    public void deleteOrganizationRole(Long orgId, Long roleId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        requireActiveOrganization(org);
        requirePermission(caller, org, "ROLE_MANAGE");
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (role.getOrganization() == null || !role.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Role does not belong to this organization");
        }
        
        roleService.deleteRole(roleId, caller);
    }

    @Transactional
    public java.util.Set<com.example.taskflow.dto.PermissionResponseDTO> updateOrganizationRolePermissions(Long orgId, Long roleId, com.example.taskflow.dto.AssignPermissionsRequestDTO request, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        requireActiveOrganization(org);
        requirePermission(caller, org, "ROLE_MANAGE");
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (role.getOrganization() == null || !role.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException("Role does not belong to this organization");
        }
        
        return roleService.assignRolePermissions(roleId, request, caller);
    }

    // ========================================================================
    // DTO MAPPING
    // ========================================================================

    private OrganizationResponseDTO mapToResponseDTO(Organization org) {
        int memberCount = (int) membershipRepository.countByOrganizationId(org.getId());
        return new OrganizationResponseDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getDescription(),
                org.getCreatedBy() != null ? org.getCreatedBy().getUsername() : null,
                org.getCreatedAt(),
                memberCount);
    }

    private MembershipResponseDTO mapToMembershipDTO(OrganizationMembership membership) {
        java.util.List<String> permissions = membership.getOrgRole() != null && membership.getOrgRole().getPermissions() != null
                ? membership.getOrgRole().getPermissions().stream().map(com.example.taskflow.domain.Permission::getName).collect(Collectors.toList())
                : java.util.Collections.emptyList();
        return new MembershipResponseDTO(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getUsername(),
                membership.getOrgRole() != null ? membership.getOrgRole().getName() : null,
                membership.getOrgRole() != null ? membership.getOrgRole().getPriority() : null,
                permissions,
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
    /**
     * RB-M03 helper: load a set of Permission entities by name.
     * Used by createOrganization to seed org-scoped builtin roles with
     * appropriate permissions at creation time.
     */
    private java.util.Set<com.example.taskflow.domain.Permission> loadPermissionsByName(String... names) {
        java.util.Set<com.example.taskflow.domain.Permission> perms = new java.util.HashSet<>();
        for (String name : names) {
            permissionRepository.findByName(name).ifPresent(perms::add);
        }
        return perms;
    }

    @Transactional
    public void leaveOrDissolveOrganization(Long orgId, User adminUser, Long successorUserId, boolean dissolve) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        requireActiveOrganization(org);

        // Require that the caller is the Admin of the organization
        OrganizationMembership adminMembership = membershipRepository.findByUserAndOrganization(adminUser, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (!adminMembership.getOrgRole().isBuiltinAdmin()) {
            throw new UnauthorizedActionException("Only the Organization Admin can perform this action");
        }

        List<OrganizationMembership> allMemberships = membershipRepository.findByOrganizationId(orgId);
        boolean isAlone = allMemberships.size() <= 1;

        if (isAlone || dissolve) {
            // Dissolve the organization completely
            
            // 1. Delete tasks in this organization
            List<Task> orgTasks = taskRepository.findByOrgId(orgId);
            taskRepository.deleteAll(orgTasks);

            // 2. Delete projects in this organization
            List<com.example.taskflow.domain.Project> orgProjects = projectRepository.findByOrganizationId(orgId);
            projectRepository.deleteAll(orgProjects);

            // 3. Delete invites in this organization
            List<com.example.taskflow.domain.OrganizationInvite> invites = inviteRepository.findByOrganizationId(orgId);
            inviteRepository.deleteAll(invites);

            // 4. Delete leave requests in this organization
            List<LeaveRequest> leaveRequests = leaveRequestRepository.findByOrganizationId(orgId);
            leaveRequestRepository.deleteAll(leaveRequests);

            // 5. Delete the organization (cascades delete to memberships, teams, custom roles)
            organizationRepository.delete(org);
            
            auditService.record("ORG_DISSOLVED", adminUser, "ORGANIZATION", orgId,
                    null, null, "Dissolved organization: " + org.getName());
        } else {
            // Transfer ownership to another member, then admin leaves
            if (successorUserId == null) {
                throw new IllegalArgumentException("A successor must be specified to transfer ownership of the organization.");
            }

            User successor = userRepository.findById(successorUserId)
                    .orElseThrow(() -> new UserNotFoundException("Successor user not found: " + successorUserId));

            OrganizationMembership successorMembership = membershipRepository.findByUserAndOrganization(successor, org)
                    .orElseThrow(() -> new IllegalArgumentException("Successor is not a member of this organization."));

            // Change successor's role to ADMIN (built-in Admin role)
            Role adminRole = roleRepository.findByNameAndOrganizationId("ADMIN", orgId)
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not found in organization"));

            successorMembership.setOrgRole(adminRole);
            membershipRepository.save(successorMembership);

            // Reassign leaving admin's active (non-terminal) tasks to the new admin
            // Bug #8 Fix: org tasks terminate at APPROVED, not COMPLETED — use isTerminal()
            taskRepository.findByAssignee(adminUser).stream()
                    .filter(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId))
                    .filter(t -> !t.getCurrentStatus().isTerminal())
                    .forEach(task -> {
                        String oldStatus = task.getCurrentStatus().name();
                        task.setAssignee(successor);
                        task.setCurrentStatus(com.example.taskflow.domain.TaskStatus.IN_PROGRESS);
                        Task updated = taskRepository.save(task);
                        taskAuditService.recordStatus(updated, oldStatus, "IN_PROGRESS", "REASSIGNED", adminUser, "Reassigned due to admin transfer");
                    });

            // Remove leaving admin from teams
            removeUserFromOrgTeams(adminUser, orgId);

            // Delete admin's membership
            membershipRepository.delete(adminMembership);

            auditService.recordSync("ORG_ADMIN_TRANSFERRED", adminUser, "ORGANIZATION", orgId,
                    adminUser.getUsername(), successor.getUsername(),
                    "Transferred admin role to " + successor.getUsername() + " and left organization");
        }
    }
}
