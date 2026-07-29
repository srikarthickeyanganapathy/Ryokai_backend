package com.example.taskflow.team.application;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.team.domain.TeamMember;
import com.example.taskflow.team.domain.TeamMemberId;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.team.dto.TeamResponseDTO;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.user.exception.UserNotFoundException;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamObserverRepository;
import com.example.taskflow.team.domain.TeamObserver;
import com.example.taskflow.team.domain.TeamObserverId;
import com.example.taskflow.team.infrastructure.persistence.TeamMemberRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.example.taskflow.notification.application.NotificationService;
import com.example.taskflow.organization.rbac.application.PermissionService;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamObserverRepository teamObserverRepository;
    private final PermissionService permissionService;

    public TeamService(TeamRepository teamRepository,
                       OrganizationRepository organizationRepository,
                       UserRepository userRepository,
                       OrganizationMembershipRepository membershipRepository,
                       TaskRepository taskRepository,
                       NotificationService notificationService,
                       TeamMemberRepository teamMemberRepository,
                       TeamObserverRepository teamObserverRepository,
                       PermissionService permissionService) {
        this.teamRepository = teamRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.teamMemberRepository = teamMemberRepository;
        this.teamObserverRepository = teamObserverRepository;
        this.permissionService = permissionService;
    }

    // ========================================================================
    // AUTH HELPERS
    // ========================================================================

    private OrganizationMembership requireOrgMembership(User user, Organization org) {
        return membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a member of this organization"));
    }

    private void requirePermission(User caller, Organization org, com.example.taskflow.security.PermissionCode permissionCode) {
        requireOrgMembership(caller, org);
        if (caller.isSuperAdmin()) return;
        permissionService.requireAuthorization(caller, permissionCode, org.getId());
    }

    // ========================================================================
    // TEAM OPERATIONS (all auth-guarded)
    // ========================================================================

    @Transactional
    public void removeUserFromAllTeams(User user, Long orgId) {
        teamRepository.findByOrganizationId(orgId).forEach(team -> {
            com.example.taskflow.team.domain.TeamMemberId tmId = new com.example.taskflow.team.domain.TeamMemberId(team.getId(), user.getId());
            if (teamMemberRepository.existsById(tmId)) {
                teamMemberRepository.deleteById(tmId);
            }
        });
    }

    @Transactional
    public TeamResponseDTO createTeam(Long orgId, String name, String description, User createdBy) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // Auth: caller must be a member of the org with MANAGER+ role
        requirePermission(createdBy, org, com.example.taskflow.security.PermissionCode.TEAM_CREATE);

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
        requirePermission(caller, org, com.example.taskflow.security.PermissionCode.TEAM_MEMBER_ADD);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Same-org check: the user being added must be a member of this org
        if (!membershipRepository.existsByUserAndOrganization(user, org)) {
            throw new IllegalArgumentException(
                "User " + user.getUsername() + " is not a member of the organization this team belongs to");
        }

        // Check if already a member
        if (teamMemberRepository.existsByIdTeamIdAndIdUserId(teamId, userId)) {
            throw new IllegalArgumentException("User is already a member of this team");
        }

        TeamMember tm = new TeamMember();
        tm.setId(new TeamMemberId(teamId, userId));
        tm.setTeam(team);
        tm.setUser(user);
        teamMemberRepository.save(tm);

        // Re-fetch team to get updated members
        Team saved = teamRepository.findById(teamId).orElseThrow();

        notificationService.createAndSend(user, caller,
            com.example.taskflow.notification.event.NotificationEvent.TEAM_MEMBER_ADDED,
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
        requirePermission(caller, org, com.example.taskflow.security.PermissionCode.TEAM_MEMBER_REMOVE);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        TeamMemberId tmId = new TeamMemberId(teamId, userId);
        if (!teamMemberRepository.existsById(tmId)) {
            throw new IllegalArgumentException("User is not a member of this team");
        }
        
        // Bug #6 Fix: Prevent removing a user who still has active tasks in the team
        // Uses non-terminal status check (excludes both APPROVED and COMPLETED)
        if (taskRepository.existsByTeamIdAndAssigneeIdAndNonTerminalStatus(teamId, userId)) {
            throw new IllegalStateException("Cannot remove member: User still has active tasks in this team. Please reassign or complete them first.");
        }
        
        teamMemberRepository.deleteById(tmId);

        // Re-fetch team to get updated members
        Team saved = teamRepository.findById(teamId).orElseThrow();

        notificationService.createAndSend(user, caller,
            com.example.taskflow.notification.event.NotificationEvent.TEAM_MEMBER_REMOVED,
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
        requirePermission(caller, team.getOrganization(), com.example.taskflow.security.PermissionCode.TEAM_UPDATE);

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
        requirePermission(caller, team.getOrganization(), com.example.taskflow.security.PermissionCode.TEAM_DELETE);
        
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
        // Find teams via TeamMember join table
        List<TeamMember> memberships = teamMemberRepository.findByIdUserId(userId);
        return memberships.stream()
                .map(tm -> mapToResponseDTO(tm.getTeam()))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // TEAM OBSERVERS
    // ========================================================================

    @Transactional(readOnly = true)
    public List<User> getTeamObservers(Long teamId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        requireOrgMembership(caller, team.getOrganization());
        
        return teamObserverRepository.findByTeam(team).stream()
                .map(obs -> obs.getUser())
                .collect(Collectors.toList());
    }

    @Transactional
    public void addObserver(Long teamId, Long userId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        requirePermission(caller, team.getOrganization(), com.example.taskflow.security.PermissionCode.TEAM_MEMBER_ADD);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        if (!membershipRepository.existsByUserAndOrganization(user, team.getOrganization())) {
            throw new IllegalArgumentException("User must be in the same organization.");
        }
        
        if (teamMemberRepository.existsByIdTeamIdAndIdUserId(teamId, userId)) {
            throw new IllegalArgumentException("User is already a full member of this team.");
        }
        
        if (teamObserverRepository.existsByIdTeamIdAndIdUserId(teamId, userId)) {
            throw new IllegalArgumentException("User is already an observer of this team.");
        }
        
        TeamObserver observer = new TeamObserver(new TeamObserverId(teamId, userId), team, user, java.time.LocalDateTime.now());
        teamObserverRepository.save(observer);
    }

    @Transactional
    public void removeObserver(Long teamId, Long userId, User caller) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));
        requirePermission(caller, team.getOrganization(), com.example.taskflow.security.PermissionCode.TEAM_MEMBER_REMOVE);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
                
        TeamObserver observer = teamObserverRepository.findByTeamAndUser(team, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not an observer of this team."));
                
        teamObserverRepository.delete(observer);
    }

    private TeamResponseDTO mapToResponseDTO(Team team) {
        java.util.List<com.example.taskflow.user.dto.UserSummaryDTO> memberDTOs = new java.util.ArrayList<>();
        if (team.getMembers() != null) {
            memberDTOs = team.getMembers().stream()
                    .map(m -> new com.example.taskflow.user.dto.UserSummaryDTO(m.getId(), m.getUsername()))
                    .collect(Collectors.toList());
        }
        return new TeamResponseDTO(
                team.getId(),
                team.getName(),
                team.getSlug(),
                team.getDescription(),
                team.getOrganization() != null ? team.getOrganization().getId() : null,
                team.getOrganization() != null ? team.getOrganization().getName() : null,
                team.getMembers() != null ? team.getMembers().size() : 0,
                memberDTOs
        );
    }
}