package com.example.taskflow.service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Permission;

@Service
public class PermissionService {

    // Cache for user permissions using Caffeine with a 5-minute TTL to automatically evict stale roles
    private final Cache<Long, Set<String>> userPermissionsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public Set<String> getPermissionsForUser(User user) {
        if (user == null || user.getId() == null) {
            return Set.of();
        }
        
        return userPermissionsCache.get(user.getId(), id -> 
            user.getRoles().stream()
                .filter(role -> role != null && role.getPermissions() != null)
                .flatMap(role -> role.getPermissions().stream())
                .filter(permission -> permission != null && permission.getName() != null)
                .map(Permission::getName)
                .collect(Collectors.toSet())
        );
    }

    public boolean hasPermission(User user, String permissionName) {
        if (user == null) return false;
        
        // SUPER_ADMIN override
        if (isSuperAdmin(user)) return true;

        Set<String> permissions = getPermissionsForUser(user);
        return permissions.contains(permissionName);
    }

    public boolean hasAnyPermission(User user, String... permissionNames) {
        if (user == null) return false;

        if (isSuperAdmin(user)) return true;

        Set<String> permissions = getPermissionsForUser(user);
        for (String perm : permissionNames) {
            if (permissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }
    
    public void invalidateCache(Long userId) {
        userPermissionsCache.invalidate(userId);
    }
    
    public void invalidateAll() {
        userPermissionsCache.invalidateAll();
    }

    private boolean isSuperAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase("SUPER_ADMIN") || r.getName().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }
}
