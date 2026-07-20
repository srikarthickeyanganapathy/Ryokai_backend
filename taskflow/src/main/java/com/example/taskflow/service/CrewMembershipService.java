package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.CrewInvite;
import com.example.taskflow.domain.CrewMember;
import com.example.taskflow.domain.CrewMemberId;
import com.example.taskflow.domain.CrewRole;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.CrewChannelDTO;
import com.example.taskflow.dto.CrewInviteDTO;
import com.example.taskflow.dto.CrewMemberDTO;
import com.example.taskflow.dto.CrewResponseDTO;
import com.example.taskflow.exception.CrewFullException;
import com.example.taskflow.exception.CrewInviteExpiredException;
import com.example.taskflow.exception.CrewNotFoundException;
import com.example.taskflow.exception.ResourceNotFoundException;
import com.example.taskflow.repository.CrewChannelRepository;
import com.example.taskflow.repository.CrewInviteRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.CrewRepository;

@Service
public class CrewMembershipService {

    private final CrewRepository crewRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CrewChannelRepository channelRepository;
    private final CrewInviteRepository inviteRepository;
    private final NotificationService notificationService;

    public CrewMembershipService(CrewRepository crewRepository,
                                 CrewMemberRepository crewMemberRepository,
                                 CrewChannelRepository channelRepository,
                                 CrewInviteRepository inviteRepository,
                                 NotificationService notificationService) {
        this.crewRepository = crewRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.channelRepository = channelRepository;
        this.inviteRepository = inviteRepository;
        this.notificationService = notificationService;
    }

    private Crew getCrewEntity(Long crewId) {
        return crewRepository.findById(crewId)
                .orElseThrow(() -> new CrewNotFoundException("Crew not found with id " + crewId));
    }

    private void validateMembership(Long crewId, User user) {
        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not a member of this crew.");
        }
    }

    private void validateCreator(Crew crew, User user) {
        if (!crew.getCreator().getId().equals(user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the crew creator can perform this action.");
        }
    }

    private CrewMemberDTO mapToMemberDTO(CrewMember m) {
        return new CrewMemberDTO(m.getUser().getId(), m.getUser().getUsername(), m.getRole(), m.getJoinedAt());
    }

    private CrewInviteDTO mapToInviteDTO(CrewInvite i) {
        return new CrewInviteDTO(i.getId(), i.getEmail(), i.getCrew().getName(), i.getInvitedBy().getUsername(), i.getExpiresAt(), i.getUsedAt());
    }

    private CrewResponseDTO mapToResponseDTO(Crew crew, User user) {
        CrewMember myMembership = crewMemberRepository.findById(new CrewMemberId(crew.getId(), user.getId())).orElse(null);
        String myRole = myMembership != null ? myMembership.getRole().name() : null;
        
        List<CrewMemberDTO> memberDTOs = crewMemberRepository.findByIdCrewId(crew.getId()).stream()
                .map(this::mapToMemberDTO)
                .collect(Collectors.toList());
                
        List<CrewChannelDTO> channelDTOs = channelRepository.findByCrew_IdOrderByPositionAsc(crew.getId()).stream()
                .map(c -> new CrewChannelDTO(c.getId(), c.getName(), c.getType(), c.getPosition(), c.getMessages().size()))
                .collect(Collectors.toList());

        return new CrewResponseDTO(
                crew.getId(),
                crew.getName(),
                crew.getSlug(),
                crew.getDescription(),
                crew.getAvatarUrl(),
                crew.getVisibility(),
                crew.getMemberCap(),
                memberDTOs.size(),
                myRole,
                crew.getCreatedAt(),
                channelDTOs,
                memberDTOs
        );
    }

    @Transactional
    public CrewResponseDTO joinPublicCrew(Long crewId, User user) {
        Crew crew = crewRepository.findByIdWithLock(crewId)
                .orElseThrow(() -> new ResourceNotFoundException("Crew not found"));

        if (crew.getVisibility() != com.example.taskflow.domain.CrewVisibility.PUBLIC) {
            throw new com.example.taskflow.exception.UnauthorizedActionException(
                    "This crew is not open for direct joining.");
        }

        long currentMembers = crewMemberRepository.findByIdCrewId(crew.getId()).size();
        if (currentMembers >= crew.getMemberCap()) {
            throw new CrewFullException("Crew member cap reached.");
        }

        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crew.getId(), user.getId())) {
            CrewMember member = new CrewMember();
            member.getId().setCrewId(crew.getId());
            member.getId().setUserId(user.getId());
            member.setCrew(crew);
            member.setUser(user);
            member.setRole(CrewRole.MEMBER);
            crewMemberRepository.save(member);

            notificationService.createAndSend(crew.getCreator(), user,
                com.example.taskflow.notification.NotificationEvent.ORG_MEMBER_JOINED,
                "New Crew Member", user.getUsername() + " joined " + crew.getName(),
                null, "crew:" + crew.getId(), user);
        }

        return mapToResponseDTO(crew, user);
    }

    @Transactional
    public CrewInviteDTO inviteMember(Long crewId, User user, String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required for email invites. Use the public-link endpoint for link invites.");
        }
        return createInvite(crewId, user, email.trim());
    }

    @Transactional
    public CrewInviteDTO createPublicLinkInvite(Long crewId, User user) {
        Crew crew = getCrewEntity(crewId);
        validateMembership(crewId, user);
        if (crew.getVisibility() != com.example.taskflow.domain.CrewVisibility.PUBLIC_LINK) {
            validateCreator(crew, user);
        }
        return createInvite(crewId, user, null);
    }

    private CrewInviteDTO createInvite(Long crewId, User user, String email) {
        Crew crew = getCrewEntity(crewId);
        validateMembership(crewId, user);

        long currentMembers = crewMemberRepository.findByIdCrewId(crewId).size();
        if (currentMembers >= crew.getMemberCap()) {
            throw new CrewFullException("Crew member cap (" + crew.getMemberCap() + ") reached.");
        }

        CrewInvite invite = new CrewInvite();
        invite.setCrew(crew);
        invite.setInvitedBy(user);
        invite.setEmail(email);
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));

        CrewInvite saved = inviteRepository.save(invite);
        return mapToInviteDTO(saved);
    }

    @Transactional
    public CrewResponseDTO acceptInvite(UUID inviteId, User user) {
        CrewInvite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (invite.getEmail() != null && !invite.getEmail().isBlank()) {
            if (invite.getUsedAt() != null) {
                throw new IllegalStateException("Invite already used.");
            }
            if (user.getEmail() == null || !invite.getEmail().equalsIgnoreCase(user.getEmail())) {
                throw new com.example.taskflow.exception.UnauthorizedActionException(
                        "This invite is for " + invite.getEmail() + ". Sign in with that email to accept.");
            }
        }

        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CrewInviteExpiredException("Invite expired.");
        }

        Crew crew = crewRepository.findByIdWithLock(invite.getCrew().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Crew not found"));
        long currentMembers = crewMemberRepository.findByIdCrewId(crew.getId()).size();
        if (currentMembers >= crew.getMemberCap()) {
            throw new CrewFullException("Crew member cap reached.");
        }

        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crew.getId(), user.getId())) {
            CrewMember member = new CrewMember();
            member.getId().setCrewId(crew.getId());
            member.getId().setUserId(user.getId());
            member.setCrew(crew);
            member.setUser(user);
            member.setRole(CrewRole.MEMBER);
            crewMemberRepository.save(member);

            notificationService.createAndSend(invite.getInvitedBy(), user,
                com.example.taskflow.notification.NotificationEvent.ORG_MEMBER_JOINED,
                "New Crew Member", user.getUsername() + " joined " + crew.getName(), null, "crew:" + crew.getId(), user);
        }

        if (invite.getEmail() != null && !invite.getEmail().isBlank()) {
            invite.setUsedAt(LocalDateTime.now());
            inviteRepository.save(invite);
        }

        return mapToResponseDTO(crew, user);
    }

    @Transactional
    public void leaveCrew(Long crewId, User user) {
        Crew crew = getCrewEntity(crewId);
        
        CrewMember member = crewMemberRepository.findById(new CrewMemberId(crewId, user.getId()))
                .orElseThrow(() -> new IllegalStateException("You are not a member of this crew"));
                
        if (member.getRole() == CrewRole.CREATOR) {
            throw new IllegalStateException("Creator cannot leave the crew. Transfer ownership or delete it.");
        }

        crewMemberRepository.delete(member);
    }

    @Transactional
    public void removeMember(Long crewId, Long targetUserId, User actor) {
        Crew crew = getCrewEntity(crewId);
        validateCreator(crew, actor);

        if (targetUserId.equals(actor.getId())) {
            throw new IllegalStateException("Cannot remove yourself via this endpoint.");
        }

        CrewMember target = crewMemberRepository.findById(new CrewMemberId(crewId, targetUserId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in crew"));

        crewMemberRepository.delete(target);
    }

    @Transactional
    public void transferOwnership(Long crewId, Long newOwnerId, User actor) {
        Crew crew = getCrewEntity(crewId);
        validateCreator(crew, actor);

        if (newOwnerId.equals(actor.getId())) {
            throw new IllegalStateException("You are already the creator of this crew.");
        }

        CrewMember currentCreator = crewMemberRepository.findById(new CrewMemberId(crewId, actor.getId()))
                .orElseThrow(() -> new IllegalStateException("You are not a member of this crew"));

        CrewMember newCreator = crewMemberRepository.findById(new CrewMemberId(crewId, newOwnerId))
                .orElseThrow(() -> new ResourceNotFoundException("Target user is not a member of this crew"));

        currentCreator.setRole(CrewRole.MEMBER);
        newCreator.setRole(CrewRole.CREATOR);

        crewMemberRepository.save(currentCreator);
        crewMemberRepository.save(newCreator);
    }

    @Transactional(readOnly = true)
    public List<CrewMemberDTO> getMembers(Long crewId, User user) {
        validateMembership(crewId, user);
        return crewMemberRepository.findByIdCrewId(crewId).stream()
                .map(this::mapToMemberDTO)
                .collect(Collectors.toList());
    }
}
