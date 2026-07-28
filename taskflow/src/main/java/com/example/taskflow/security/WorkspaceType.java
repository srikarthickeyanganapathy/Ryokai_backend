package com.example.taskflow.security;
import com.example.taskflow.crew.domain.Crew;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.user.domain.User;

/**
 * Workspace types in Ryokai. Every authorization decision starts by
 * resolving which workspace type the request targets.
 *
 * <ul>
 *   <li>{@link #PERSONAL} â€” No RBAC, owner has full control</li>
 *   <li>{@link #CREW} â€” Fixed roles (OWNER/MEMBER), no permission database</li>
 *   <li>{@link #ORGANIZATION} â€” Full enterprise RBAC with permissions, scopes, and policies</li>
 * </ul>
 */
public enum WorkspaceType {

    /**
     * Personal workspace â€” single owner, no roles, no permissions.
     * Authorization check: {@code user.id == resource.owner.id}.
     */
    PERSONAL,

    /**
     * Crew workspace â€” fixed OWNER/MEMBER roles.
     * No configurable permissions, no scopes, no policies.
     */
    CREW,

    /**
     * Organization workspace â€” full enterprise RBAC.
     * Roles, permissions, scopes, resource assignments, policies, field restrictions.
     */
    ORGANIZATION
}