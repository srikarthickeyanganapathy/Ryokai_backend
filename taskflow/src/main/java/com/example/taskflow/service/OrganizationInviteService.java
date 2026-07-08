package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationInvite;
import com.example.taskflow.domain.OrganizationInvite.InviteStatus;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.OrgRole;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.OrganizationInviteDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.repository.OrganizationInviteRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.UserRepository;

@Service
public class OrganizationInviteService {

    private final OrganizationInviteRepository inviteRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public OrganizationInviteService(OrganizationInviteRepository inviteRepository,
                                      OrganizationRepository organizationRepository,
                                      OrganizationMembershipRepository membershipRepository,
                                      UserRepository userRepository,
                                      NotificationService notificationService) {
        this.inviteRepository = inviteRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public OrganizationInviteDTO createInAppInvite(Long orgId, Long inviteeUserId, OrgRole orgRole, User invitedBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        User invitee = userRepository.findById(inviteeUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + inviteeUserId));

        // Auth: caller must be org ADMIN
        OrganizationMembership inviterMembership = membershipRepository.findByUserAndOrganization(invitedBy, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
        if (inviterMembership.getOrgRole() != OrgRole.ADMIN) {
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

        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrganization(org);
        invite.setInvitedBy(invitedBy);
        invite.setInviteeUser(invitee);
        invite.setOrgRole(orgRole);
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        invite.setCreatedAt(LocalDateTime.now());
        OrganizationInvite saved = inviteRepository.save(invite);

        // Send in-app notification to invitee
        notificationService.createAndSend(invitee, invitedBy, NotificationEvent.ORG_INVITE_RECEIVED,
        "" + invitedBy.getUsername() + " has invited you to join " + org.getName(), "Organization Invitation", null, 
            "org-invite:" + saved.getId());

        return toDTO(saved);
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
        if (membership.getOrgRole() != OrgRole.ADMIN) {
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
            user.getUsername() + " has accepted your invitation to join " + invite.getOrganization().getName(),
            "Invite Accepted", null, "org-joined:" + invite.getOrganization().getId());

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
        if (membership.getOrgRole() != OrgRole.ADMIN) {
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
            invite.getOrgRole().name(),
            invite.getStatus().name(),
            invite.getExpiresAt(),
            invite.getCreatedAt()
        );
    }
}
