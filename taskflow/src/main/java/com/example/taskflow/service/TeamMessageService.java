package com.example.taskflow.service;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.TeamMessage;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TeamMessageCreateRequestDTO;
import com.example.taskflow.dto.TeamMessageResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.repository.TeamMessageRepository;
import com.example.taskflow.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamMessageService {

    private final TeamMessageRepository teamMessageRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public TeamMessageService(TeamMessageRepository teamMessageRepository,
                              TeamRepository teamRepository,
                              TeamMemberRepository teamMemberRepository,
                              OrganizationMembershipRepository membershipRepository) {
        this.teamMessageRepository = teamMessageRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.membershipRepository = membershipRepository;
    }

    private void validateAccess(Team team, User user) {
        // 1. Must be org member
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, team.getOrganization())
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of the organization"));

        // 2. Must be in the team OR manager/director/admin (priority <= 60)
        boolean isTeamMember = teamMemberRepository.existsByIdTeamIdAndIdUserId(team.getId(), user.getId());
        boolean isManagerOrAbove = membership.getOrgRole() != null && 
                (membership.getOrgRole().getPriority() <= 60 || "ADMIN".equals(membership.getOrgRole().getName()));

        if (!isTeamMember && !isManagerOrAbove) {
            throw new UnauthorizedActionException("You must be a team member or a manager to access this team's discussions");
        }
    }

    @Transactional(readOnly = true)
    public List<TeamMessageResponseDTO> getMessages(Long teamId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        validateAccess(team, caller);

        return teamMessageRepository.findByTeamIdOrderByCreatedAtAsc(teamId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamMessageResponseDTO sendMessage(Long teamId, TeamMessageCreateRequestDTO request, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        validateAccess(team, caller);

        TeamMessage message = new TeamMessage();
        message.setTeam(team);
        message.setAuthor(caller);
        message.setContent(request.getContent());

        TeamMessage saved = teamMessageRepository.save(message);
        return mapToResponseDTO(saved);
    }

    @Transactional
    public void deleteMessage(Long teamId, Long messageId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        
        TeamMessage message = teamMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getTeam().getId().equals(teamId)) {
            throw new IllegalArgumentException("Message does not belong to team " + teamId);
        }

        // Auth: caller must be author OR org priority <= 60 (manager/director/admin)
        boolean isAuthor = message.getAuthor().getId().equals(caller.getId());
        
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(caller, team.getOrganization())
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of the organization"));
        boolean isManagerOrAbove = membership.getOrgRole() != null && 
                (membership.getOrgRole().getPriority() <= 60 || "ADMIN".equals(membership.getOrgRole().getName()));

        if (!isAuthor && !isManagerOrAbove) {
            throw new UnauthorizedActionException("You are not authorized to delete this message");
        }

        teamMessageRepository.delete(message);
    }

    private TeamMessageResponseDTO mapToResponseDTO(TeamMessage msg) {
        return new TeamMessageResponseDTO(
                msg.getId(),
                msg.getTeam().getId(),
                msg.getAuthor().getId(),
                msg.getAuthor().getUsername(),
                msg.getContent(),
                msg.getCreatedAt()
        );
    }
}
