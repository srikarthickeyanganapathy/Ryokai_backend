package com.example.taskflow.goal.security;

import com.example.taskflow.goal.domain.Goal;
import com.example.taskflow.goal.infrastructure.persistence.GoalRepository;
import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationRequestBuilder;
import com.example.taskflow.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.io.Serializable;

@Component
@RequiredArgsConstructor
public class GoalAuthorizationResolver implements AuthorizationResourceResolver {

    private final GoalRepository goalRepository;
    private final AuthorizationRequestBuilder requestBuilder;

    @Override
    public boolean supportsClass(Class<?> targetClass) {
        return Goal.class.isAssignableFrom(targetClass) || requestBuilder.supportsDto(targetClass, "GOAL");
    }

    @Override
    public boolean supportsResourceType(String resourceType) {
        return "Goal".equalsIgnoreCase(resourceType);
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permission) {
        if (targetDomainObject instanceof Goal goal) {
            java.util.Map<String, Long> context = new java.util.HashMap<>();
            if (goal.getOrganization() != null) {
                context.put("organizationId", goal.getOrganization().getId());
            }
            java.util.Set<com.example.taskflow.security.authorization.OwnershipRole> ownership = java.util.EnumSet.noneOf(com.example.taskflow.security.authorization.OwnershipRole.class);
            if (goal.getOwner() != null && goal.getOwner().getId().equals(user.getId())) {
                ownership.add(com.example.taskflow.security.authorization.OwnershipRole.CREATOR);
            }
            com.example.taskflow.security.ScopeType scope = com.example.taskflow.security.ScopeType.valueOf(com.example.taskflow.security.PermissionMetadataRegistry.getRecommendedScope(permission.name()));
            
            return requestBuilder.build(user, permission, "Goal", goal.getId(), com.example.taskflow.security.WorkspaceType.ORGANIZATION, scope, context, ownership);
        }
        
        return requestBuilder.buildFromDto(user, permission, targetDomainObject, "GOAL");
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permission) {
        if (targetId instanceof Long id) {
            Goal goal = goalRepository.findById(id).orElse(null);
            if (goal != null) {
                return buildRequest(auth, user, goal, permission);
            }
        }
        return null;
    }
}
