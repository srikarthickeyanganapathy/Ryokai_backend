package com.example.taskflow.security.platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines which platform permissions each platform role has.
 *
 * <p>This is the platform equivalent of {@code role_permission_scopes}.
 * Since platform roles are fixed (not tenant-configurable), the mapping
 * is defined as code constants — no database table needed.
 */
public final class PlatformRolePermissions {

    private static final Map<PlatformRole, Set<PlatformPermission>> ROLE_PERMISSIONS;

    static {
        EnumMap<PlatformRole, Set<PlatformPermission>> map = new EnumMap<>(PlatformRole.class);

        // PLATFORM_OWNER: all permissions
        map.put(PlatformRole.PLATFORM_OWNER,
                EnumSet.allOf(PlatformPermission.class));

        // PLATFORM_ADMIN: everything except billing and platform settings mutation
        EnumSet<PlatformPermission> adminPerms = EnumSet.allOf(PlatformPermission.class);
        adminPerms.remove(PlatformPermission.PLATFORM_SETTINGS_UPDATE);
        adminPerms.remove(PlatformPermission.BILLING_UPDATE);
        adminPerms.remove(PlatformPermission.PLATFORM_USER_ROLE_UPDATE);
        map.put(PlatformRole.PLATFORM_ADMIN, adminPerms);

        // PLATFORM_SUPPORT: read-only + impersonation + org details
        map.put(PlatformRole.PLATFORM_SUPPORT, EnumSet.of(
                PlatformPermission.ORG_VIEW_ALL,
                PlatformPermission.ORG_VIEW_DETAILS,
                PlatformPermission.PLATFORM_USER_VIEW,
                PlatformPermission.PLATFORM_ANALYTICS_VIEW,
                PlatformPermission.PLATFORM_HEALTH_VIEW,
                PlatformPermission.PLATFORM_SETTINGS_VIEW,
                PlatformPermission.FEATURE_FLAG_VIEW,
                PlatformPermission.BILLING_VIEW,
                PlatformPermission.IMPERSONATE_USER,
                PlatformPermission.AUDIT_LOG_VIEW
        ));

        ROLE_PERMISSIONS = Collections.unmodifiableMap(map);
    }

    private PlatformRolePermissions() {}

    /**
     * Returns all permissions granted to the given platform role.
     */
    public static Set<PlatformPermission> getPermissions(PlatformRole role) {
        return ROLE_PERMISSIONS.getOrDefault(role, EnumSet.noneOf(PlatformPermission.class));
    }

    /**
     * Returns true if the given role has the given permission.
     */
    public static boolean hasPermission(PlatformRole role, PlatformPermission permission) {
        Set<PlatformPermission> perms = ROLE_PERMISSIONS.get(role);
        return perms != null && perms.contains(permission);
    }
}
