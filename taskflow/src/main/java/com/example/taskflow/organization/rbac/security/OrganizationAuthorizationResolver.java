package com.example.taskflow.organization.rbac.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.WorkspaceType;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationRequestBuilder;
import com.example.taskflow.security.authorization.OwnershipRole;
import com.example.taskflow.security.authorization.WorkspaceContextResolver;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.user.domain.User;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class OrganizationAuthorizationResolver implements AuthorizationResourceResolver {

    private final OrganizationRepository orgRepository;
    private final AuthorizationRequestBuilder requestBuilder;
    private final WorkspaceContextResolver contextResolver;

    public OrganizationAuthorizationResolver(OrganizationRepository orgRepository,
                                             AuthorizationRequestBuilder requestBuilder,
                                             WorkspaceContextResolver contextResolver) {
        this.orgRepository = orgRepository;
        this.requestBuilder = requestBuilder;
        this.contextResolver = contextResolver;
    }

    @Override
    public boolean supportsResourceType(String resourceType) {
        return "Organization".equalsIgnoreCase(resourceType);
    }

    @Override
    public boolean supportsClass(Class<?> targetClass) {
        return Organization.class.isAssignableFrom(targetClass) || requestBuilder.supportsDto(targetClass, "ORGANIZATION");
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode) {
        if (targetDomainObject instanceof Organization org) {
            Map<String, Long> context = new HashMap<>();
            context.put("organizationId", org.getId());
    
            Set<OwnershipRole> ownership = EnumSet.noneOf(OwnershipRole.class);
            if (org.getCreatedBy() != null && org.getCreatedBy().getId().equals(user.getId())) {
                ownership.add(OwnershipRole.CREATOR);
            }
    
            return requestBuilder.build(
                    user,
                    permissionCode,
                    "ORGANIZATION",
                    org.getId(),
                    WorkspaceType.ORGANIZATION,
                    ScopeType.ORGANIZATION,
                    context,
                    ownership
            );
        } else if (requestBuilder.supportsDto(targetDomainObject.getClass(), "ORGANIZATION")) {
            return requestBuilder.buildFromDto(user, permissionCode, targetDomainObject, "ORGANIZATION");
        }
        return null;
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode) {
        Long orgId = null;
        if (targetId instanceof Long id) {
            orgId = id;
        } else if (targetId == null) {
            orgId = contextResolver.resolveOrgIdForUser(user);
        }
        
        if (orgId == null) return null;
        
        Organization org = orgRepository.findById(orgId).orElse(null);
        if (org == null) {
            // Even if the org entity isn't fully loaded, we can build a request with the ID
            return requestBuilder.build(
                user,
                permissionCode,
                "ORGANIZATION",
                orgId,
                WorkspaceType.ORGANIZATION,
                ScopeType.ORGANIZATION,
                java.util.Map.of("organizationId", orgId),
                EnumSet.noneOf(OwnershipRole.class)
            );
        }
        return buildRequest(auth, user, org, permissionCode);
    }
}
