package com.example.taskflow.security.authorization;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates all context needed for a single authorization evaluation.
 *
 * <p>This object is passed through every stage of the {@link AuthorizationPipeline}.
 * It gathers all inputs once to avoid repeated lookups.
 */
public class AuthorizationRequest {

    private final User user;
    private final PermissionCode permission;
    private final String resourceType;
    private final Long resourceId;
    private final Long organizationId;
    private final Long teamId;
    private final Long projectId;
    private final Set<String> modifiedFields;
    private final Map<String, Object> policyContext;

    private AuthorizationRequest(Builder builder) {
        this.user = builder.user;
        this.permission = builder.permission;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.organizationId = builder.organizationId;
        this.teamId = builder.teamId;
        this.projectId = builder.projectId;
        this.modifiedFields = builder.modifiedFields != null
                ? Collections.unmodifiableSet(builder.modifiedFields)
                : Collections.emptySet();
        this.policyContext = builder.policyContext != null
                ? Collections.unmodifiableMap(builder.policyContext)
                : Collections.emptyMap();
    }

    // â”€â”€ Getters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public User getUser() { return user; }
    public PermissionCode getPermission() { return permission; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Long getOrganizationId() { return organizationId; }
    public Long getTeamId() { return teamId; }
    public Long getProjectId() { return projectId; }
    public Set<String> getModifiedFields() { return modifiedFields; }
    public Map<String, Object> getPolicyContext() { return policyContext; }

    /**
     * Returns the most specific scope level that applies to this request.
     * Used by the scope resolver to determine if the user's grant is sufficient.
     */
    public ScopeType getRequiredScope() {
        if (resourceId != null && resourceType != null) {
            // Check if the resource is owned by the user (OWN scope)
            // This is determined by the caller, not here â€” we return the
            // minimum structural scope based on the provided context.
        }
        if (projectId != null) return ScopeType.PROJECT;
        if (teamId != null) return ScopeType.TEAM;
        if (organizationId != null) return ScopeType.ORGANIZATION;
        return ScopeType.OWN;
    }

    // â”€â”€ Builder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static Builder builder(User user, PermissionCode permission) {
        return new Builder(user, permission);
    }

    public static class Builder {
        private final User user;
        private final PermissionCode permission;
        private String resourceType;
        private Long resourceId;
        private Long organizationId;
        private Long teamId;
        private Long projectId;
        private Set<String> modifiedFields;
        private Map<String, Object> policyContext;

        private Builder(User user, PermissionCode permission) {
            this.user = user;
            this.permission = permission;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(Long resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder organizationId(Long organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public Builder teamId(Long teamId) {
            this.teamId = teamId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder modifiedFields(Set<String> modifiedFields) {
            this.modifiedFields = modifiedFields;
            return this;
        }

        /**
         * Additional context for policy evaluation (e.g., the target resource entity).
         */
        public Builder policyContext(Map<String, Object> policyContext) {
            this.policyContext = policyContext;
            return this;
        }

        public AuthorizationRequest build() {
            if (user == null) throw new IllegalArgumentException("User is required");
            if (permission == null) throw new IllegalArgumentException("Permission is required");
            return new AuthorizationRequest(this);
        }
    }
}