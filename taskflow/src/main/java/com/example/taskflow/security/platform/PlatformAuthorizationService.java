package com.example.taskflow.security.platform;

import com.example.taskflow.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authorization service for platform-level operations.
 *
 * <p>This service is the <b>sole entry point</b> for platform authorization.
 * It evaluates whether a user has a platform identity and the required
 * platform permission. It <b>never</b> calls the workspace RBAC pipeline.
 *
 * <h3>Architecture Boundary</h3>
 * <pre>
 * Platform Layer          │  Workspace Layer
 * ────────────────────────┼────────────────────────
 * PlatformAuthService     │  AuthorizationPipeline
 * PlatformRole            │  PermissionCode
 * PlatformPermission      │  ScopeType
 * PlatformRolePermissions │  PolicyEvaluator
 *                         │
 * /api/v1/platform/**     │  /api/v1/organizations/**
 *                         │  /api/v1/tasks/**
 *                         │  /api/v1/projects/**
 * </pre>
 *
 * <p>The two systems <b>never call each other</b>.
 */
@Service
public class PlatformAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthorizationService.class);

    /**
     * Checks whether the given user has a platform identity.
     */
    public boolean isPlatformIdentity(User user) {
        return resolvePlatformRole(user) != null;
    }

    /**
     * Resolves the platform role for a user.
     * Returns null if the user is not a platform identity.
     */
    public PlatformRole resolvePlatformRole(User user) {
        if (user == null || user.getRoles() == null) return null;

        PlatformRole highest = null;
        for (var role : user.getRoles()) {
            String name = role.getName();
            PlatformRole pr = PlatformRole.fromLegacyRoleName(name);
            if (pr != null) {
                if (highest == null || pr.outranks(highest)) {
                    highest = pr;
                }
            }
        }
        return highest;
    }

    /**
     * Checks whether the user has the required platform permission.
     *
     * @param user       the user (must be a platform identity)
     * @param permission the required platform permission
     * @return true if the user's platform role includes the permission
     */
    public boolean hasPermission(User user, PlatformPermission permission) {
        PlatformRole role = resolvePlatformRole(user);
        if (role == null) {
            log.debug("Platform authorization denied: user {} is not a platform identity",
                    user != null ? user.getId() : "null");
            return false;
        }
        boolean granted = PlatformRolePermissions.hasPermission(role, permission);
        if (!granted) {
            log.debug("Platform authorization denied: {} does not have {} (role: {})",
                    user.getId(), permission, role);
        }
        return granted;
    }

    /**
     * Throws if the user does not have the required platform permission.
     */
    public void requirePermission(User user, PlatformPermission permission) {
        if (!hasPermission(user, permission)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException(
                    "This action requires platform permission: " + permission.name());
        }
    }
}
