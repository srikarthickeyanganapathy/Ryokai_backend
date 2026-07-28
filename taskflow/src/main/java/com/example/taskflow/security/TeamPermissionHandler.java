package com.example.taskflow.security;

import java.io.Serializable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.TeamRepository;
import com.example.taskflow.service.PermissionService;
import com.example.taskflow.security.authorization.LegacyPermissionMapper;

@Component
public class TeamPermissionHandler implements DomainPermissionHandler {

    private final TeamRepository teamRepository;
    private final PermissionService permissionService;

    public TeamPermissionHandler(TeamRepository teamRepository,
                                 PermissionService permissionService) {
        this.teamRepository = teamRepository;
        this.permissionService = permissionService;
    }

    @Override
    public String getTargetType() {
        return "Team";
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Object targetDomainObject, String permission) {
        if (targetDomainObject instanceof Team team) {
            return checkTeamPermission(user, team, permission);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission) {
        if (targetId == null) return false;
        Long teamId;
        try {
            teamId = ((Number) targetId).longValue();
        } catch (ClassCastException e) {
            return false;
        }

        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) return false;

        return checkTeamPermission(user, team, permission);
    }

    private boolean checkTeamPermission(User user, Team team, String permission) {
        if (team == null || team.getOrganization() == null) return false;

        PermissionCode code = LegacyPermissionMapper.resolveForDomain("TEAM", permission);
        if (code == null) {
            return false;
        }

        return permissionService.isAuthorized(user, code, team.getOrganization().getId(), "TEAM", team.getId());
    }
}
