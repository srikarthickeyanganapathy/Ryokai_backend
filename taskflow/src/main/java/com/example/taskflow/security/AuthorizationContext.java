package com.example.taskflow.security;

import java.util.Set;

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
