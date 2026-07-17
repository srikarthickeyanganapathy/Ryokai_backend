package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationInvite;
import com.example.taskflow.domain.OrganizationInvite.InviteStatus;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.OrganizationInviteDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.repository.OrganizationInviteRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.repository.RoleRepository;

@Service
public class OrganizationInviteService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OrganizationInviteRepository inviteRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RoleRepository roleRepository;

    public OrganizationInviteService(OrganizationInviteRepository inviteRepository,
                                      OrganizationRepository organizationRepository,
                                      OrganizationMembershipRepository membershipRepository,
                                      UserRepository userRepository,
                                      NotificationService notificationService,
                                      AuditService auditService,
                                      RoleRepository roleRepository) {
        this.inviteRepository = inviteRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public OrganizationInviteDTO createInAppInvite(Long orgId, Long inviteeUserId, Long roleId, User invitedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        User invitee = userRepository.findById(inviteeUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + inviteeUserId));

        // Auth: caller must be org ADMIN
        OrganizationMembership inviterMembership = membershipRepository.findByUserAndOrganization(invitedBy, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (!inviterMembership.getOrgRole().isBuiltinAdmin()) {
            throw new UnauthorizedActionException("Only the Organization Admin can send invites");
        }

        // Cannot invite if already a member
        if (membershipRepository.existsByUserAndOrganization(invitee, org)) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }

        // Cannot invite if already has a pending invite
        if (inviteRepository.existsByInviteeUserIdAndOrganizationIdAndStatus(inviteeUserId, orgId, InviteStatus.PENDING)) {
            throw new IllegalArgumentException("User already has a pending invite to this organization");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (role.isBuiltinAdmin()) {
            throw new IllegalArgumentException("Only one Admin is allowed in the organization. You cannot invite another Admin.");
        }

        // RB-M02 fix: verify the role belongs to the inviting organization.
        // Previously an org admin could attach a role from ANOTHER org (or a
        // global builtin) to an invite, leaking foreign permission grants.
        if (role.getOrganization() == null
                || !role.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException(
                "Role does not belong to this organization. Cross-org role assignment is not allowed.");
        }

        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrganization(org);
        invite.setInvitedBy(invitedBy);
        invite.setInviteeUser(invitee);
        invite.setOrgRole(role);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setCreatedAt(LocalDateTime.now());
        OrganizationInvite saved = inviteRepository.save(invite);

        // Send in-app notification to invitee
        notificationService.createAndSend(invitee, invitedBy, NotificationEvent.ORG_INVITE_RECEIVED,
            "Organization Invitation", invitedBy.getUsername() + " has invited you to join " + org.getName(), null, 
            "org-invite:" + saved.getId(), invitedBy);

        OrganizationInviteDTO dto = toDTO(saved);
        auditService.record("ORG_MEMBER_INVITED", invitedBy, "ORGANIZATION", org.getId(),
                null, dto, "Invited user " + invitee.getUsername() + " with role " + role.getName());

        return dto;
    }

    @Transactional
    public OrganizationInviteDTO createShareableLink(Long orgId, Long roleId, User invitedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        OrganizationMembership inviterMembership = membershipRepository.findByUserAndOrganization(invitedBy, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (!inviterMembership.getOrgRole().isBuiltinAdmin()) {
            throw new UnauthorizedActionException("Only the Organization Admin can create shareable links");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (role.isBuiltinAdmin()) {
            throw new IllegalArgumentException("Only one Admin is allowed in the organization. You cannot invite another Admin.");
        }

        // RB-M02 fix: same cross-org role check as createInAppInvite above.
        if (role.getOrganization() == null
                || !role.getOrganization().getId().equals(orgId)) {
            throw new IllegalArgumentException(
                "Role does not belong to this organization. Cross-org role assignment is not allowed.");
        }

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrganization(org);
        invite.setInvitedBy(invitedBy);
        invite.setOrgRole(role);
        invite.setStatus(InviteStatus.PENDING);
        invite.setToken(token);
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setCreatedAt(LocalDateTime.now());
        OrganizationInvite saved = inviteRepository.save(invite);

        OrganizationInviteDTO dto = toDTO(saved);
        auditService.record("ORG_LINK_CREATED", invitedBy, "ORGANIZATION", org.getId(),
                null, dto, "Created shareable link with role " + role.getName());

        return dto;
    }

    @Transactional(readOnly = true)
    public List<OrganizationInviteDTO> getMyPendingInvites(User user) {
        return inviteRepository.findByInviteeUserIdAndStatus(user.getId(), InviteStatus.PENDING).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrganizationInviteDTO> getOrgInvites(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(caller, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (!membership.getOrgRole().isBuiltinAdmin()) {
            throw new UnauthorizedActionException("Only the Organization Admin can view invites");
        }
        return inviteRepository.findByOrganizationId(orgId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationInviteDTO acceptInvite(Long inviteId, User user) {
        OrganizationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found: " + inviteId));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("This invite is no longer pending");
        }

        if (!invite.getInviteeUser().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("This invite is not for you");
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            inviteRepository.save(invite);
            throw new IllegalStateException("This invite has expired");
        }

        // One-org rule: user can only be in one org
        if (!membershipRepository.findByUserId(user.getId()).isEmpty()) {
            throw new IllegalStateException("You are already a member of an organization. Leave your current organization first.");
        }

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(LocalDateTime.now());
        inviteRepository.save(invite);

        // Create membership
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(user);
        membership.setOrganization(invite.getOrganization());
        membership.setOrgRole(invite.getOrgRole());
        membershipRepository.save(membership);

        // Notify the inviter
        notificationService.createAndSend(invite.getInvitedBy(), user, NotificationEvent.ORG_INVITE_ACCEPTED,
            "Invite Accepted",
            user.getUsername() + " has accepted your invitation to join " + invite.getOrganization().getName(),
             null, "org-joined:" + invite.getOrganization().getId(), user);

        return toDTO(invite);
    }

    @Transactional
    public OrganizationInviteDTO acceptInviteByToken(String token, User user) {
        OrganizationInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("This invite is no longer pending");
        }

        if (invite.getInviteeUser() != null && !invite.getInviteeUser().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("This invite is not for you");
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            inviteRepository.save(invite);
            throw new IllegalStateException("This invite has expired");
        }

        if (!membershipRepository.findByUserId(user.getId()).isEmpty()) {
            throw new IllegalStateException("You are already a member of an organization. Leave your current organization first.");
        }

        // If it was a shareable link, bind it to the accepting user now
        if (invite.getInviteeUser() == null) {
            invite.setInviteeUser(user);
        }

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(LocalDateTime.now());
        inviteRepository.save(invite);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(user);
        membership.setOrganization(invite.getOrganization());
        membership.setOrgRole(invite.getOrgRole());
        membershipRepository.save(membership);

        notificationService.createAndSend(invite.getInvitedBy(), user, NotificationEvent.ORG_INVITE_ACCEPTED,
            "Invite Accepted",
            user.getUsername() + " has accepted your invitation to join " + invite.getOrganization().getName(),
             null, "org-joined:" + invite.getOrganization().getId(), user);

        return toDTO(invite);
    }

    @Transactional
    public OrganizationInviteDTO declineInvite(Long inviteId, User user) {
        OrganizationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found: " + inviteId));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("This invite is no longer pending");
        }

        if (!invite.getInviteeUser().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("This invite is not for you");
        }

        invite.setStatus(InviteStatus.DECLINED);
        OrganizationInvite saved = inviteRepository.save(invite);
        return toDTO(saved);
    }

    @Transactional
    public OrganizationInviteDTO revokeInvite(Long inviteId, User adminUser) {
        OrganizationInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found: " + inviteId));

        Organization org = invite.getOrganization();
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(adminUser, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (!membership.getOrgRole().isBuiltinAdmin()) {
            throw new UnauthorizedActionException("Only the Organization Admin can revoke invites");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("Only pending invites can be revoked");
        }

        invite.setStatus(InviteStatus.REVOKED);
        OrganizationInvite saved = inviteRepository.save(invite);
        return toDTO(saved);
    }

    private OrganizationInviteDTO toDTO(OrganizationInvite invite) {
        return new OrganizationInviteDTO(
            invite.getId(),
            invite.getOrganization().getId(),
            invite.getOrganization().getName(),
            invite.getInvitedBy() != null ? invite.getInvitedBy().getUsername() : null,
            invite.getInviteeUser() != null ? invite.getInviteeUser().getUsername() : null,
            invite.getOrgRole().getName(),
            invite.getStatus().name(),
            invite.getToken(),
            invite.getExpiresAt(),
            invite.getCreatedAt()
        );
    }
}
