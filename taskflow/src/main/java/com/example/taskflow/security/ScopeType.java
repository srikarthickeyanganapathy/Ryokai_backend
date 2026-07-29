package com.example.taskflow.security;

/**
 * Authorization scope levels for Organization RBAC.
 * Scopes determine <b>where</b> a permission applies.
 *
 * <p>Scope hierarchy (higher includes lower):
 * {@code ORGANIZATION âŠ‡ TEAM âŠ‡ PROJECT âŠ‡ OWN}
 *
 * <p>Personal and Crew workspaces do NOT use scopes.
 */
public enum ScopeType {

    /**
     * Applies only to resources owned by or assigned to the user.
     * Lowest scope â€” most restrictive.
     */
    OWN(0),

    /**
     * Applies within a specific project.
     */
    PROJECT(10),

    /**
     * Applies within a specific team.
     */
    TEAM(20),

    /**
     * Applies to all resources within the organization.
     * Highest scope within the org RBAC model.
     */
    ORGANIZATION(30);

    private final int priority;

    ScopeType(int priority) {
        this.priority = priority;
    }

    /**
     * Returns the numeric priority. Higher values = broader scope.
     * Used for scope inheritance: a scope includes all scopes with lower priority.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns true if this scope includes (is broader than or equal to) the other scope.
     */
    public boolean includes(ScopeType other) {
        return this.priority >= other.priority;
    }
}