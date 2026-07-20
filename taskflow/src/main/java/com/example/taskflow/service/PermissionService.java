package com.example.taskflow.service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Permission;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.repository.OrganizationMembershipRepository;

@Service
public class PermissionService {

    private final OrganizationMembershipRepository membershipRepository;

    // Cache for user permissions using Caffeine with a 5-minute TTL to automatically evict stale roles
    private final Cache<Long, Set<String>> userPermissionsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public PermissionService(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /**
     * Returns the set of permission names granted to the user.
     *
     * RB-C03 fix: previously this method only aggregated permissions from
     * user.getRoles() (the global user_roles join, which holds only SUPER_ADMIN).
     * Per the spec and the comment in RoleStrategyFactory, every non-SUPER_ADMIN
     * user has an empty roles set  -  so this method returned an empty set for
     * everyone except SUPER_ADMIN, making @PreAuthorize("hasPermission(null, 'X')")
     * unreachable for org users.
     *
     * Fixed to ALSO aggregate permissions from OrganizationMembership.orgRole.permissions
     * for every org the user belongs to. SUPER_ADMIN still gets the global short-circuit
     * in hasPermission() below.
     */
    public Set<String> getPermissionsForUser(User user) {
        if (user == null || user.getId() == null) {
            return Set.of();
        }

        return userPermissionsCache.get(user.getId(), id -> {
            Set<String> perms = new HashSet<>();

            // 1. Global roles (user_roles join  -  typically only SUPER_ADMIN)
            if (user.getRoles() != null) {
                user.getRoles().stream()
                    .filter(role -> role != null && role.getPermissions() != null)
                    .flatMap(role -> role.getPermissions().stream())
                    .filter(permission -> permission != null && permission.getName() != null)
                    .map(Permission::getName)
                    .forEach(perms::add);
            }

            // 2. Org-scoped roles via OrganizationMembership.orgRole.permissions
            //    This is where ADMIN/DIRECTOR/MANAGER/EMPLOYEE/custom role
            //    permissions actually live.
            for (OrganizationMembership m : membershipRepository.findByUserId(user.getId())) {
                if (m.getOrgRole() != null) {
                    if (m.getOrgRole().getPermissions() != null) {
                        m.getOrgRole().getPermissions().stream()
                            .filter(p -> p != null && p.getName() != null)
                            .map(Permission::getName)
                            .forEach(perms::add);
                    }
                }
            }

            return perms;
        });
    }

    public boolean hasPermission(User user, String permissionName) {
        if (user == null) return false;

        // SUPER_ADMIN override
        if (user.isSuperAdmin()) return true;

        Set<String> permissions = getPermissionsForUser(user);
        return permissions.contains(permissionName);
    }

    public boolean hasAnyPermission(User user, String... permissionNames) {
        if (user == null) return false;

        if (user.isSuperAdmin()) return true;

        Set<String> permissions = getPermissionsForUser(user);
        for (String perm : permissionNames) {
            if (permissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    public OrganizationMembership requirePermission(User user, com.example.taskflow.domain.Organization org, String permission) {
        if (user.isSuperAdmin()) return null; // Super Admin bypasses
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new com.example.taskflow.exception.UnauthorizedActionException("You are not a member of this organization"));
        if (membership.getOrgRole() == null || membership.getOrgRole().getPermissions().stream().noneMatch(p -> p.getName().equals(permission))) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("This action requires the " + permission + " permission.");
        }
        return membership;
    }

    public void invalidateCache(Long userId) {
        userPermissionsCache.invalidate(userId);
    }

    public void invalidateAll() {
        userPermissionsCache.invalidateAll();
    }


}
