package com.example.taskflow.security.platform;

/**
 * Platform-level roles for the Ryokai SaaS platform itself.
 *
 * <p>These are <b>NOT</b> organization roles. Platform identities exist outside
 * any workspace. They manage organizations, billing, feature flags, and
 * platform health — they never interact with workspace RBAC.
 *
 * <p>A platform identity is a user with a {@code user_roles} entry pointing
 * to a role whose name matches one of these values.
 */
public enum PlatformRole {

    /**
     * Full platform control. Can create, suspend, delete organizations.
     * Manages platform-level settings, billing, and feature flags.
     */
    PLATFORM_OWNER(0, "Full platform control"),

    /**
     * Administrative access to the platform. Can manage organizations,
     * view platform analytics, and perform user management.
     * Cannot modify platform-level settings or billing.
     */
    PLATFORM_ADMIN(10, "Platform administration"),

    /**
     * Support-level access. Can view organization details, impersonate
     * users for debugging, and access support tools.
     * Cannot create, suspend, or delete organizations.
     */
    PLATFORM_SUPPORT(20, "Support and debugging access");

    private final int priority;
    private final String description;

    PlatformRole(int priority, String description) {
        this.priority = priority;
        this.description = description;
    }

    public int priority() { return priority; }
    public String description() { return description; }

    /**
     * Returns true if this role has equal or higher authority than the given role.
     * Lower priority number = higher authority.
     */
    public boolean outranks(PlatformRole other) {
        return this.priority <= other.priority;
    }

    /**
     * Maps the legacy SUPER_ADMIN role name to the new platform role.
     * Returns null if the name is not a platform role.
     */
    public static PlatformRole fromLegacyRoleName(String roleName) {
        if (roleName == null) return null;
        String normalized = roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;
        return switch (normalized) {
            case "SUPER_ADMIN" -> PLATFORM_OWNER;
            case "PLATFORM_OWNER" -> PLATFORM_OWNER;
            case "PLATFORM_ADMIN" -> PLATFORM_ADMIN;
            case "PLATFORM_SUPPORT" -> PLATFORM_SUPPORT;
            default -> null;
        };
    }
}
