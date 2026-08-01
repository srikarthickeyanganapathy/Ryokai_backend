package com.example.taskflow.security.authorization;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates all context needed for a single authorization evaluation.
 */
public class AuthorizationRequest {

    private final User user;
    private final PermissionCode permission;
    private final String resourceType;
    private final Long resourceId;
    private final Map<String, Object> policyContext;
    private final Map<String, Long> context;
    private final ScopeType requiredScope;
    private final com.example.taskflow.security.WorkspaceType workspaceType;
    private final Set<OwnershipRole> ownership;

    private AuthorizationRequest(Builder builder) {
        this.user = builder.user;
        this.permission = builder.permission;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.requiredScope = builder.requiredScope;
        this.workspaceType = builder.workspaceType != null ? builder.workspaceType : com.example.taskflow.security.WorkspaceType.ORGANIZATION;
        this.ownership = builder.ownership != null ? Collections.unmodifiableSet(builder.ownership) : Collections.emptySet();
        this.context = builder.context != null ? Collections.unmodifiableMap(builder.context) : Collections.emptyMap();
        this.policyContext = builder.policyContext != null ? Collections.unmodifiableMap(builder.policyContext) : Collections.emptyMap();
    }

    public User getUser() { return user; }
    public PermissionCode getPermission() { return permission; }
    public PermissionCode getAction() { return permission; }
    public String getResourceType() { return resourceType; }
    public Long getResourceId() { return resourceId; }
    public Map<String, Object> getPolicyContext() { return policyContext; }
    public Map<String, Long> getContext() { return context; }
    public ScopeType getRequiredScope() { return requiredScope; }
    

    public com.example.taskflow.security.WorkspaceType getWorkspaceType() { return workspaceType; }
    public Set<OwnershipRole> getOwnership() { return ownership; }
    
    public static Builder builder(User user, PermissionCode permission) {
        return new Builder(user, permission);
    }

    public static class Builder {
        private final User user;
        private final PermissionCode permission;
        private String resourceType;
        private Long resourceId;
        private Map<String, Object> policyContext;
        private Map<String, Long> context;
        private ScopeType requiredScope;
        private com.example.taskflow.security.WorkspaceType workspaceType;
        private Set<OwnershipRole> ownership;

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

        public Builder context(Map<String, Long> context) {
            this.context = context;
            return this;
        }

        public Builder policyContext(Map<String, Object> policyContext) {
            this.policyContext = policyContext;
            return this;
        }
        
        
        public Builder workspaceType(com.example.taskflow.security.WorkspaceType workspaceType) {
            this.workspaceType = workspaceType;
            return this;
        }

        public Builder ownership(Set<OwnershipRole> ownership) {
            this.ownership = ownership;
            return this;
        }

        public Builder requiredScope(ScopeType requiredScope) {
            this.requiredScope = requiredScope;
            return this;
        }

        public AuthorizationRequest build() {
            if (user == null) throw new IllegalArgumentException("User is required");
            if (permission == null) throw new IllegalArgumentException("Permission is required");
            return new AuthorizationRequest(this);
        }
    }
}
