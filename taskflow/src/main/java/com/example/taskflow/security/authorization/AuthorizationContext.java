package com.example.taskflow.security.authorization;

import java.util.Set;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.security.ImpersonationSession;

public record AuthorizationContext(
    Long userId,
    Long organizationId,
    String role,
    int rolePriority,
    Set<Long> crewIds,
    Set<String> permissions,
    boolean isPlatformAdmin,
    ImpersonationSession impersonation
) {
    public boolean hasPermission(String permission) {
        if (permissions == null) return false;
        return permissions.contains(permission) || permissions.contains("*");
    }

    public boolean isInCrew(Long crewId) {
        return crewIds != null && crewIds.contains(crewId);
    }
}