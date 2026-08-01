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

    public OrganizationAuthorizationResolver(OrganizationRepository orgRepository,
                                             AuthorizationRequestBuilder requestBuilder) {
        this.orgRepository = orgRepository;
        this.requestBuilder = requestBuilder;
    }

    @Override
    public String getTargetType() {
        return "Organization";
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode) {
        if (!(targetDomainObject instanceof Organization org)) return null;

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
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode) {
        if (!(targetId instanceof Long orgId)) return null;
        Organization org = orgRepository.findById(orgId).orElse(null);
        if (org == null) return null;
        return buildRequest(auth, user, org, permissionCode);
    }
}
