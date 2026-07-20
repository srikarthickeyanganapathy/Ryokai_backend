package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.ChannelType;
import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.CrewChannel;
import com.example.taskflow.domain.CrewInvite;
import com.example.taskflow.domain.CrewMember;
import com.example.taskflow.domain.CrewMemberId;
import com.example.taskflow.domain.CrewRole;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.CrewChannelDTO;
import com.example.taskflow.dto.CrewInviteDTO;
import com.example.taskflow.dto.CrewMemberDTO;
import com.example.taskflow.dto.CrewRequestDTO;
import com.example.taskflow.dto.CrewResponseDTO;
import com.example.taskflow.exception.CrewFullException;
import com.example.taskflow.exception.CrewInviteExpiredException;
import com.example.taskflow.exception.CrewNotFoundException;
import com.example.taskflow.exception.ResourceNotFoundException;
import com.example.taskflow.repository.CrewChannelRepository;
import com.example.taskflow.repository.CrewInviteRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.CrewRepository;
import com.example.taskflow.repository.ProjectRepository;

@Service
public class CrewService {

    private final CrewRepository crewRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CrewChannelRepository channelRepository;
    private final CrewInviteRepository inviteRepository;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;
    private final com.example.taskflow.repository.TaskRepository taskRepository;
    private final ProjectService projectService;

    public CrewService(CrewRepository crewRepository,
                       CrewMemberRepository crewMemberRepository,
                       CrewChannelRepository channelRepository,
                       CrewInviteRepository inviteRepository,
                       ProjectRepository projectRepository,
                       NotificationService notificationService,
                       com.example.taskflow.repository.TaskRepository taskRepository,
                       ProjectService projectService) {
        this.crewRepository = crewRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.channelRepository = channelRepository;
        this.inviteRepository = inviteRepository;
        this.projectRepository = projectRepository;
        this.notificationService = notificationService;
        this.taskRepository = taskRepository;
        this.projectService = projectService;
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
        crewMemberRepository.saveAndFlush(member);

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
        
        if (taskRepository.existsByCrewId(crewId)) {
            throw new IllegalStateException("Cannot delete crew: Please delete all tasks within the crew first.");
        }
        
        crewRepository.delete(crew);
    }

    @Transactional(readOnly = true)
    public Page<CrewResponseDTO> discoverCrews(String keyword, Pageable pageable, User user) {
        Page<Crew> page = crewRepository.searchPublicCrews(
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(), pageable);
        return page.map(crew -> mapToResponseDTO(crew, user));
    }

    // --- Internal Helpers ---
    
    private CrewMemberDTO mapToMemberDTO(CrewMember m) {
        return new CrewMemberDTO(m.getUser().getId(), m.getUser().getUsername(), m.getRole(), m.getJoinedAt());
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

    // --- Extracted Project Sharing (CrewProjectService) ---
}
