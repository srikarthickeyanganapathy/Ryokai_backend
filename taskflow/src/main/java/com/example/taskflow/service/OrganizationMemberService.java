package com.example.taskflow.service;

import com.example.taskflow.domain.*;
import com.example.taskflow.dto.MembershipResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.exception.UserNotFoundException;
import com.example.taskflow.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrganizationMemberService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TaskRepository taskRepository;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final TeamService teamService;

    public OrganizationMemberService(OrganizationRepository organizationRepository,
                                     OrganizationMembershipRepository membershipRepository,
                                     UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     TaskRepository taskRepository,
                                     AuditService auditService,
                                     PermissionService permissionService,
                                     TeamService teamService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.taskRepository = taskRepository;
        this.auditService = auditService;
        this.permissionService = permissionService;
        this.teamService = teamService;
    }

    @Transactional
    public MembershipResponseDTO inviteMember(Long orgId, Long userId, Long roleId, User invitedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        org.requireActive();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

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

    @Transactional
    public void removeMember(Long orgId, Long userId, User removedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (removedBy.getId().equals(userId)) {
            throw new UnauthorizedActionException(
                    "Admins cannot remove themselves directly. Use the leave request workflow instead.");
        }

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization"));

        org.ensureNotLastAdmin(user);

        boolean hasPendingTasks = taskRepository.findByAssignee(user).stream()
                .anyMatch(t -> t.getOrg() != null && t.getOrg().getId().equals(orgId) &&
                               !t.getCurrentStatus().isTerminal());
        
        if (hasPendingTasks) {
            throw new IllegalStateException("Cannot remove member because they have pending tasks. Please reassign their tasks first.");
        }

        teamService.removeUserFromAllTeams(user, orgId);

        membershipRepository.delete(membership);
        
        auditService.recordSync("ORG_MEMBER_REMOVED", removedBy, "ORGANIZATION", org.getId(),
                user.getUsername(), null, "Removed member " + user.getUsername() + " from organization");
    }

    @Transactional
    public MembershipResponseDTO updateMemberRole(Long orgId, Long userId, Long newRoleId, User callerUser) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization"));

        Role newRole = roleRepository.findById(newRoleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (newRole.isBuiltinAdmin()) {
            throw new IllegalArgumentException("Only one Admin is allowed in the organization. You cannot promote another member to Admin. Use the Transfer Ownership flow instead.");
        }

        if (newRole.getOrganization() == null
                || !newRole.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException(
                "Role does not belong to this organization. Cross-org role assignment is not allowed.");
        }

        if (callerUser.getId().equals(userId) && membership.getOrgRole().isBuiltinAdmin() && !newRole.isBuiltinAdmin()) {
            org.ensureNotLastAdmin(user);
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
    public List<MembershipResponseDTO> listOrganizationMembers(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        return membershipRepository.findByOrganizationId(orgId).stream()
                .map(this::mapToMembershipDTO)
                .collect(Collectors.toList());
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
}
