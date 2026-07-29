package com.example.taskflow.security.platform;

/**
 * Platform-level permissions for Ryokai SaaS operations.
 *
 * <p>These permissions control operations on the <b>platform itself</b>,
 * not within any organization workspace. They are evaluated by
 * {@link PlatformAuthorizationService} and are completely independent
 * from the workspace RBAC system.
 *
 * <p>Platform operations include:
 * <ul>
 *   <li>Organization lifecycle (create, suspend, delete, restore)</li>
 *   <li>Platform user management (list, create, disable platform identities)</li>
 *   <li>Platform analytics and health monitoring</li>
 *   <li>Billing and subscription management</li>
 *   <li>Feature flag management</li>
 * </ul>
 */
public enum PlatformPermission {

    // â”€â”€ Organization Lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    ORG_CREATE("Create a new organization tenant"),
    ORG_SUSPEND("Suspend an organization"),
    ORG_UNSUSPEND("Reactivate a suspended organization"),
    ORG_DELETE("Permanently delete an organization"),
    ORG_VIEW_ALL("View all organizations on the platform"),
    ORG_VIEW_DETAILS("View detailed organization internals for support"),

    // â”€â”€ Platform User Management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    PLATFORM_USER_VIEW("View platform user directory"),
    PLATFORM_USER_CREATE("Create platform identity (admin/support)"),
    PLATFORM_USER_DISABLE("Disable a platform identity"),
    PLATFORM_USER_ROLE_UPDATE("Change a platform user's role"),

    // â”€â”€ Platform Analytics â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    PLATFORM_ANALYTICS_VIEW("View platform-wide analytics"),
    PLATFORM_HEALTH_VIEW("View platform health and infrastructure status"),

    // â”€â”€ Platform Settings â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    PLATFORM_SETTINGS_VIEW("View platform configuration"),
    PLATFORM_SETTINGS_UPDATE("Modify platform configuration"),
    FEATURE_FLAG_VIEW("View feature flags"),
    FEATURE_FLAG_UPDATE("Toggle feature flags"),

    // â”€â”€ Billing â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    BILLING_VIEW("View billing and subscription data"),
    BILLING_UPDATE("Modify billing configuration"),

    // â”€â”€ Support Tools â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    IMPERSONATE_USER("Impersonate a user for debugging"),
    AUDIT_LOG_VIEW("View platform audit logs");

    private final String description;

    PlatformPermission(String description) {
        this.description = description;
    }

    public String description() { return description; }
}