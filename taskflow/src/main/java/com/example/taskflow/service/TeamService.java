package com.example.taskflow.service;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.RoleCategory;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TeamResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.exception.UserNotFoundException;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.TeamRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    public TeamService(TeamRepository teamRepository,
                       OrganizationRepository organizationRepository,
                       UserRepository userRepository,
                       OrganizationMembershipRepository membershipRepository,
                       TaskRepository taskRepository,
                       NotificationService notificationService) {
        this.teamRepository = teamRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
    }

    // ========================================================================
    // AUTH HELPERS
    // ========================================================================

    private OrganizationMembership requireOrgMembership(User user, Organization org) {
        return membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
    }

    private void requireManagerOrAbove(User caller, Organization org) {
        OrganizationMembership membership = requireOrgMembership(caller, org);
        RoleCategory role = membership.getOrgRole() != null ? membership.getOrgRole().getCategory() : null;
        if (role != RoleCategory.BUILTIN_ADMIN && role != RoleCategory.BUILTIN_DIRECTOR && role != RoleCategory.BUILTIN_MANAGER) {
            throw new UnauthorizedActionException("Only Managers, Directors, or Admins can manage teams");
        }
    }

    // ========================================================================
    // TEAM OPERATIONS (all auth-guarded)
    // ========================================================================

    @Transactional
    public TeamResponseDTO createTeam(Long orgId, String name, String description, User createdBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Auth: caller must be a member of the org with MANAGER+ role
        requireManagerOrAbove(createdBy, org);

        Team team = new Team();
        team.setName(name);
        team.setDescription(description);
        team.setOrganization(org);
        team.setCreatedBy(createdBy);
        Team saved = teamRepository.save(team);

        return mapToResponseDTO(saved);
    }

    @Transactional
    public TeamResponseDTO addTeamMember(Long teamId, Long userId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        Organization org = team.getOrganization();

        // Auth: caller must be MANAGER+ in the same org
        requireManagerOrAbove(caller, org);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Same-org check: the user being added must be a member of this org
        if (!membershipRepository.existsByUserAndOrganization(user, org)) {
            throw new IllegalArgumentException(
                "User " + user.getUsername() + " is not a member of the organization this team belongs to");
        }

        team.getMembers().add(user);
        Team saved = teamRepository.save(team);

        notificationService.createAndSend(user, caller,
            com.example.taskflow.notification.NotificationEvent.TEAM_MEMBER_ADDED,
            "Added to Team",
            "You have been added to team " + team.getName(),
            null,
            "team-add:" + team.getId() + ":" + user.getId(),
            caller);

        return mapToResponseDTO(saved);
    }

    @Transactional
    public TeamResponseDTO removeTeamMember(Long teamId, Long userId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        Organization org = team.getOrganization();

        // Auth: caller must be MANAGER+ in the same org
        requireManagerOrAbove(caller, org);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        team.getMembers().remove(user);
        Team saved = teamRepository.save(team);

        notificationService.createAndSend(user, caller,
            com.example.taskflow.notification.NotificationEvent.TEAM_MEMBER_REMOVED,
            "Removed from Team",
            "You have been removed from team " + team.getName(),
            null,
            "team-remove:" + team.getId() + ":" + user.getId(),
            caller);

        return mapToResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public TeamResponseDTO getTeam(Long teamId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        // Auth: caller must be a member of the org this team belongs to
        requireOrgMembership(caller, team.getOrganization());
        return mapToResponseDTO(team);
    }

    @Transactional
    public TeamResponseDTO updateTeam(Long teamId, String name, String description, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        // Auth: caller must be MANAGER+ in the same org
        requireManagerOrAbove(caller, team.getOrganization());

        if (name != null && !name.isBlank()) team.setName(name);
        if (description != null) team.setDescription(description);
        Team saved = teamRepository.save(team);
        return mapToResponseDTO(saved);
    }

    @Transactional
    public void deleteTeam(Long teamId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        // Auth: caller must be MANAGER+ in the same org
        requireManagerOrAbove(caller, team.getOrganization());
        
        long taskCount = taskRepository.countByTeamId(teamId);
        if (taskCount > 0) {
            throw new IllegalStateException("Cannot delete team because it has " + taskCount + " tasks assigned to it. Please reassign or archive these tasks first.");
        }
        
        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public List<TeamResponseDTO> listOrgTeams(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Auth: caller must be a member of this org
        requireOrgMembership(caller, org);

        return teamRepository.findByOrganizationId(orgId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamResponseDTO> listUserTeams(Long userId) {
        return teamRepository.findByMembersId(userId).stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    private TeamResponseDTO mapToResponseDTO(Team team) {
        java.util.List<com.example.taskflow.dto.UserSummaryDTO> memberDTOs = new java.util.ArrayList<>();
        if (team.getMembers() != null) {
            memberDTOs = team.getMembers().stream()
                    .map(m -> new com.example.taskflow.dto.UserSummaryDTO(m.getId(), m.getUsername()))
                    .collect(Collectors.toList());
        }
        return new TeamResponseDTO(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getOrganization() != null ? team.getOrganization().getId() : null,
                team.getOrganization() != null ? team.getOrganization().getName() : null,
                team.getMembers() != null ? team.getMembers().size() : 0,
                memberDTOs
        );
    }
}
