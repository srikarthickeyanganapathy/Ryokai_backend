package com.example.taskflow.security;

/**
 * Workspace types in Ryokai. Every authorization decision starts by
 * resolving which workspace type the request targets.
 *
 * <ul>
 *   <li>{@link #PERSONAL} — No RBAC, owner has full control</li>
 *   <li>{@link #CREW} — Fixed roles (OWNER/MEMBER), no permission database</li>
 *   <li>{@link #ORGANIZATION} — Full enterprise RBAC with permissions, scopes, and policies</li>
 * </ul>
 */
public enum WorkspaceType {

    /**
     * Personal workspace — single owner, no roles, no permissions.
     * Authorization check: {@code user.id == resource.owner.id}.
     */
    PERSONAL,

    /**
     * Crew workspace — fixed OWNER/MEMBER roles.
     * No configurable permissions, no scopes, no policies.
     */
    CREW,

    /**
     * Organization workspace — full enterprise RBAC.
     * Roles, permissions, scopes, resource assignments, policies, field restrictions.
     */
    ORGANIZATION
}
