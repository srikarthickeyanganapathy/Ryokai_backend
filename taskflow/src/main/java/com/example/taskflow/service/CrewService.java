package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.ChannelType;
import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.CrewChannel;
import com.example.taskflow.domain.CrewInvite;
import com.example.taskflow.domain.CrewMember;
import com.example.taskflow.domain.CrewMemberId;
import com.example.taskflow.domain.CrewProject;
import com.example.taskflow.domain.CrewProjectId;
import com.example.taskflow.domain.CrewRole;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.CrewChannelDTO;
import com.example.taskflow.dto.CrewInviteDTO;
import com.example.taskflow.dto.CrewMemberDTO;
import com.example.taskflow.dto.CrewRequestDTO;
import com.example.taskflow.dto.CrewResponseDTO;
import com.example.taskflow.dto.ProjectSummaryDTO;
import com.example.taskflow.exception.CrewFullException;
import com.example.taskflow.exception.CrewInviteExpiredException;
import com.example.taskflow.exception.CrewNotFoundException;
import com.example.taskflow.exception.ResourceNotFoundException;
import com.example.taskflow.repository.CrewChannelRepository;
import com.example.taskflow.repository.CrewInviteRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.CrewProjectRepository;
import com.example.taskflow.repository.CrewRepository;
import com.example.taskflow.repository.ProjectRepository;

@Service
public class CrewService {

    private final CrewRepository crewRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CrewChannelRepository channelRepository;
    private final CrewInviteRepository inviteRepository;
    private final CrewProjectRepository crewProjectRepository;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;

    public CrewService(CrewRepository crewRepository,
                       CrewMemberRepository crewMemberRepository,
                       CrewChannelRepository channelRepository,
                       CrewInviteRepository inviteRepository,
                       CrewProjectRepository crewProjectRepository,
                       ProjectRepository projectRepository,
                       NotificationService notificationService) {
        this.crewRepository = crewRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.channelRepository = channelRepository;
        this.inviteRepository = inviteRepository;
        this.crewProjectRepository = crewProjectRepository;
        this.projectRepository = projectRepository;
        this.notificationService = notificationService;
    }

    private String generateSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Transactional
    public CrewResponseDTO createCrew(User user, CrewRequestDTO dto) {
        Crew crew = new Crew();
        crew.setName(dto.getName());
        
        String baseSlug = generateSlug(dto.getName());
        String slug = baseSlug;
        int counter = 1;
        while (crewRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter++;
        }
        crew.setSlug(slug);
        
        crew.setDescription(dto.getDescription());
        crew.setAvatarUrl(dto.getAvatarUrl());
        crew.setVisibility(dto.getVisibility());
        crew.setMemberCap(dto.getMemberCap());
        crew.setCreator(user);
        
        Crew savedCrew = crewRepository.save(crew);

        // Auto-add creator as member
        CrewMember member = new CrewMember();
        member.getId().setCrewId(savedCrew.getId());
        member.getId().setUserId(user.getId());
        member.setCrew(savedCrew);
        member.setUser(user);
        member.setRole(CrewRole.CREATOR);
        crewMemberRepository.save(member);

        // Auto-create #general channel
        CrewChannel general = new CrewChannel();
        general.setCrew(savedCrew);
        general.setName("general");
        general.setType(ChannelType.TEXT);
        general.setPosition(0);
        channelRepository.save(general);

        return mapToResponseDTO(savedCrew, user);
    }

    @Transactional(readOnly = true)
    public CrewResponseDTO getCrew(Long crewId, User user) {
        Crew crew = getCrewEntity(crewId);
        validateMembership(crewId, user);
        return mapToResponseDTO(crew, user);
    }

    @Transactional(readOnly = true)
    public List<CrewResponseDTO> getMyCrews(User user) {
        List<CrewMember> memberships = crewMemberRepository.findByIdUserId(user.getId());
        return memberships.stream()
                .map(m -> mapToResponseDTO(m.getCrew(), user))
                .collect(Collectors.toList());
    }

    @Transactional
    public CrewResponseDTO updateCrew(Long crewId, User user, CrewRequestDTO dto) {
        Crew crew = getCrewEntity(crewId);
        validateCreator(crew, user);

        crew.setName(dto.getName());
        crew.setDescription(dto.getDescription());
        crew.setAvatarUrl(dto.getAvatarUrl());
        crew.setVisibility(dto.getVisibility());
        crew.setMemberCap(dto.getMemberCap());

        return mapToResponseDTO(crewRepository.save(crew), user);
    }

    @Transactional
    public void deleteCrew(Long crewId, User user) {
        Crew crew = getCrewEntity(crewId);
        validateCreator(crew, user);
        crewRepository.delete(crew);
    }

    // --- Membership & Invites ---

    @Transactional
    public CrewInviteDTO inviteMember(Long crewId, User user, String email) {
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

        if (invite.getUsedAt() != null) {
            throw new IllegalStateException("Invite already used.");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CrewInviteExpiredException("Invite expired.");
        }

        Crew crew = invite.getCrew();
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
            
            // We use string 'CREW_JOINED' instead of adding to enum to avoid modifying NotificationEvent unnecessarily
            notificationService.createAndSend(invite.getInvitedBy(), user, 
                com.example.taskflow.notification.NotificationEvent.ORG_MEMBER_JOINED, // fallback enum
                "New Crew Member", user.getUsername() + " joined " + crew.getName(), null, "crew:" + crew.getId(), user);
        }

        invite.setUsedAt(LocalDateTime.now());
        inviteRepository.save(invite);

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

    @Transactional(readOnly = true)
    public List<CrewMemberDTO> getMembers(Long crewId, User user) {
        validateMembership(crewId, user);
        return crewMemberRepository.findByIdCrewId(crewId).stream()
                .map(this::mapToMemberDTO)
                .collect(Collectors.toList());
    }

    // --- Projects ---

    @Transactional
    public void shareProject(Long crewId, Long projectId, User actor) {
        validateMembership(crewId, actor);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        
        if (project.getCreatedBy() == null || !project.getCreatedBy().getId().equals(actor.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the project owner can share it.");
        }

        if (project.getOrganization() != null) {
            throw new IllegalStateException("Cannot share organization projects with a Crew. Only personal projects can be shared.");
        }

        CrewProjectId cpId = new CrewProjectId(crewId, projectId);
        if (!crewProjectRepository.existsById(cpId)) {
            CrewProject cp = new CrewProject();
            cp.setId(cpId);
            cp.setCrew(getCrewEntity(crewId));
            cp.setProject(project);
            crewProjectRepository.save(cp);
        }
    }

    @Transactional
    public void unshareProject(Long crewId, Long projectId, User actor) {
        validateMembership(crewId, actor);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
                
        if (project.getCreatedBy() == null || !project.getCreatedBy().getId().equals(actor.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the project owner can unshare it.");
        }

        crewProjectRepository.findById(new CrewProjectId(crewId, projectId))
                .ifPresent(crewProjectRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDTO> getCrewProjects(Long crewId, User requester) {
        validateMembership(crewId, requester);
        return crewProjectRepository.findByIdCrewId(crewId).stream()
                .map(cp -> new ProjectSummaryDTO(
                        cp.getProject().getId(),
                        cp.getProject().getName(),
                        cp.getProject().getDescription(),
                        cp.getProject().getColor(),
                        cp.getProject().getDueDate()
                )).collect(Collectors.toList());
    }

    // --- Internal Helpers ---

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

    private CrewMemberDTO mapToMemberDTO(CrewMember m) {
        return new CrewMemberDTO(m.getUser().getId(), m.getUser().getUsername(), m.getRole(), m.getJoinedAt());
    }

    private CrewInviteDTO mapToInviteDTO(CrewInvite i) {
        return new CrewInviteDTO(i.getId(), i.getEmail(), i.getCrew().getName(), i.getInvitedBy().getUsername(), i.getExpiresAt(), i.getUsedAt());
    }
}
