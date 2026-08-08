package com.example.taskflow.team.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.WorkspaceType;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationRequestBuilder;
import com.example.taskflow.security.authorization.OwnershipRole;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamMemberRepository;
import com.example.taskflow.user.domain.User;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class TeamAuthorizationResolver implements AuthorizationResourceResolver {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthorizationRequestBuilder requestBuilder;

    public TeamAuthorizationResolver(TeamRepository teamRepository,
                                     TeamMemberRepository teamMemberRepository,
                                     AuthorizationRequestBuilder requestBuilder) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.requestBuilder = requestBuilder;
    }

    @Override
    public boolean supportsResourceType(String resourceType) {
        return "Team".equalsIgnoreCase(resourceType);
    }

    @Override
    public boolean supportsClass(Class<?> targetClass) {
        return Team.class.isAssignableFrom(targetClass) || requestBuilder.supportsDto(targetClass, "TEAM");
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode) {
        if (targetDomainObject instanceof Team team) {
            Map<String, Long> context = new HashMap<>();
            if (team.getOrganization() != null) context.put("organizationId", team.getOrganization().getId());
            context.put("teamId", team.getId());
    
            Set<OwnershipRole> ownership = EnumSet.noneOf(OwnershipRole.class);
            if (team.getCreatedBy() != null && team.getCreatedBy().getId().equals(user.getId())) {
                ownership.add(OwnershipRole.CREATOR);
            }
            if (teamMemberRepository.existsByIdTeamIdAndIdUserId(team.getId(), user.getId())) {
                ownership.add(OwnershipRole.TEAM_MEMBER);
            }
            
            return requestBuilder.build(
                    user,
                    permissionCode,
                    "TEAM",
                    team.getId(),
                    WorkspaceType.ORGANIZATION,
                    ScopeType.TEAM,
                    context,
                    ownership
            );
        } else if (requestBuilder.supportsDto(targetDomainObject.getClass(), "TEAM")) {
            return requestBuilder.buildFromDto(user, permissionCode, targetDomainObject, "TEAM");
        }
        return null;
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode) {
        if (!(targetId instanceof Long teamId)) return null;
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team == null) return null;
        return buildRequest(auth, user, team, permissionCode);
    }
}
