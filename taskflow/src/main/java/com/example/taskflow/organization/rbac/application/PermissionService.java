package com.example.taskflow.organization.rbac.application;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationPipeline;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.rbac.domain.RolePermissionScope;
import com.example.taskflow.shared.exception.UnauthorizedActionException;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final OrganizationMembershipRepository membershipRepository;
    private final AuthorizationPipeline authorizationPipeline;

    // Cache for user permissions using Caffeine with a 5-minute TTL to automatically evict stale roles
    private final Cache<Long, Set<String>> userPermissionsCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public PermissionService(OrganizationMembershipRepository membershipRepository,
                             AuthorizationPipeline authorizationPipeline) {
        this.membershipRepository = membershipRepository;
        this.authorizationPipeline = authorizationPipeline;
    }

    // ========================================================================
    // NEW: Pipeline-based authorization methods
    // ========================================================================

    /**
     * Evaluates authorization using the full pipeline.
     *
     * <p>This is the primary method for new code. It checks permissions,
     * scopes, policies, and field restrictions in a single call.
     *
     * @param request the fully-constructed authorization request
     * @return the authorization decision (GRANT or DENY with reason)
     */
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        return authorizationPipeline.evaluate(request);
    }

    /**
     * Convenience: checks a single permission for a user in an organization.
     */
    public boolean isAuthorized(User user, PermissionCode permission, Long organizationId) {
        AuthorizationRequest request = AuthorizationRequest.builder(user, permission)
                .organizationId(organizationId)
                .build();
        return authorizationPipeline.evaluate(request).isGranted();
    }

    /**
     * Convenience: checks a permission for a user on a specific resource.
     */
    public boolean isAuthorized(User user, PermissionCode permission,
                                Long organizationId, String resourceType, Long resourceId) {
        AuthorizationRequest request = AuthorizationRequest.builder(user, permission)
                .organizationId(organizationId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .build();
        return authorizationPipeline.evaluate(request).isGranted();
    }

    /**
     * Throws UnauthorizedActionException if the user does not have the permission.
     */
    public void requireAuthorization(User user, PermissionCode permission, Long organizationId) {
        AuthorizationRequest request = AuthorizationRequest.builder(user, permission)
                .organizationId(organizationId)
                .build();
        AuthorizationDecision decision = authorizationPipeline.evaluate(request);
        if (decision.isDenied()) {
            throw new com.example.taskflow.shared.exception.UnauthorizedActionException(
                    "This action requires the " + permission.code() + " permission. "
                    + "Denied at stage: " + decision.stage());
        }
    }

    // ========================================================================
    // LEGACY: Preserved for backward compatibility during migration
    // ========================================================================

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
     *
     * @deprecated Use {@link #isAuthorized(User, PermissionCode, Long)} instead.
     */
    @Deprecated(forRemoval = true)
    public Set<String> getPermissionsForUser(User user) {
        if (user == null || user.getId() == null) {
            return Set.of();
        }

        return userPermissionsCache.get(user.getId(), id -> {
            Set<String> perms = new HashSet<>();

            // 1. Global roles (user_roles join  -  typically only SUPER_ADMIN)
            if (user.getRoles() != null) {
                user.getRoles().stream()
                    .filter(role -> role != null && role.getRolePermissionScopes() != null)
                    .flatMap(role -> role.getRolePermissionScopes().stream())
                    .map(com.example.taskflow.organization.rbac.domain.RolePermissionScope::getPermission)
                    .filter(permission -> permission != null && permission.getName() != null)
                    .map(Permission::getName)
                    .forEach(perms::add);
            }

            // 2. Org-scoped roles via OrganizationMembership.orgRole.permissions
            //    This is where ADMIN/DIRECTOR/MANAGER/EMPLOYEE/custom role
            //    permissions actually live.
            for (OrganizationMembership m : membershipRepository.findByUserId(user.getId())) {
                if (m.getOrgRole() != null) {
                    if (m.getOrgRole().getRolePermissionScopes() != null) {
                        m.getOrgRole().getRolePermissionScopes().stream()
                            .map(com.example.taskflow.organization.rbac.domain.RolePermissionScope::getPermission)
                            .filter(p -> p != null && p.getName() != null)
                            .map(Permission::getName)
                            .forEach(perms::add);
                    }
                }
            }

            return perms;
        });
    }

    /**
     * @deprecated Use {@link #isAuthorized(User, PermissionCode, Long)} instead.
     */
    @Deprecated(forRemoval = true)
    public boolean hasPermission(User user, String permissionName) {
        if (user == null) return false;

        // SUPER_ADMIN override
        if (user.isSuperAdmin()) return true;

        Set<String> permissions = getPermissionsForUser(user);
        return permissions.contains(permissionName);
    }

    /**
     * @deprecated Use {@link #isAuthorized(User, PermissionCode, Long)} instead.
     */
    @Deprecated(forRemoval = true)
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

    /**
     * @deprecated Use {@link #requireAuthorization(User, PermissionCode, Long)} instead.
     */
    @Deprecated(forRemoval = true)
    public OrganizationMembership requirePermission(User user, com.example.taskflow.organization.core.domain.Organization org, String permission) {
        if (user.isSuperAdmin()) return null; // Super Admin bypasses
        OrganizationMembership membership = membershipRepository.findByUserAndOrganization(user, org)
                .orElseThrow(() -> new com.example.taskflow.shared.exception.UnauthorizedActionException("You are not a member of this organization"));
        if (membership.getOrgRole() == null || membership.getOrgRole().getRolePermissionScopes().stream().noneMatch(rps -> rps.getPermission().getName().equals(permission))) {
            throw new com.example.taskflow.shared.exception.UnauthorizedActionException("This action requires the " + permission + " permission.");
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