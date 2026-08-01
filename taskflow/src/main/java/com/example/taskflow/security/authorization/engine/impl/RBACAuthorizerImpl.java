package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.rbac.domain.RolePermissionScope;
import com.example.taskflow.organization.rbac.domain.UserPermissionOverride;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RolePermissionScopeRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.ResourceAssignmentRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.UserPermissionOverrideRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.PermissionImplications;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;

import com.example.taskflow.security.authorization.engine.RBACAuthorizer;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RBACAuthorizerImpl implements RBACAuthorizer {

    private final OrganizationMembershipRepository membershipRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final RolePermissionScopeRepository rpsRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;

    public RBACAuthorizerImpl(OrganizationMembershipRepository membershipRepository,
                              UserPermissionOverrideRepository overrideRepository,
                              RolePermissionScopeRepository rpsRepository,
                              ResourceAssignmentRepository resourceAssignmentRepository) {
        this.membershipRepository = membershipRepository;
        this.overrideRepository = overrideRepository;
        this.rpsRepository = rpsRepository;
        this.resourceAssignmentRepository = resourceAssignmentRepository;
    }

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        if (request.getUser() == null || request.getUser().getId() == null) {
            return AuthorizationDecision.deny("RBAC", "User must be authenticated for RBAC check");
        }

        if (request.getUser().isSuperAdmin()) {
            return AuthorizationDecision.allow("RBAC", "SuperAdmin bypass");
        }

        Long orgId = extractOrganizationId(request);
        if (orgId == null) {
            return AuthorizationDecision.abstain("Not an organization workspace request");
        }

        OrganizationMembership membership = membershipRepository.findByUserIdAndOrganizationId(request.getUser().getId(), orgId)
                .orElse(null);

        if (membership == null) {
            return AuthorizationDecision.deny("RBAC", "User is not a member of the organization");
        }

        if (membership.getOrgRole() == null) {
            return AuthorizationDecision.deny("RBAC", "User has no role in the organization");
        }

        List<Long> roleIds = new ArrayList<>();
        roleIds.add(membership.getOrgRole().getId());

        // Check overrides
        List<UserPermissionOverride> overrides = overrideRepository.findActiveByUserAndOrg(
                request.getUser().getId(), orgId, LocalDateTime.now());

        for (UserPermissionOverride override : overrides) {
            if (override.getPermission().getCode().equals(request.getAction().code())) {
                if ("DENY".equals(override.getOverrideType())) {
                    return AuthorizationDecision.deny("RBAC", "Explicitly denied by user override");
                }
                if ("GRANT".equals(override.getOverrideType())) {
                    return AuthorizationDecision.allow("RBAC", "Explicitly granted by user override");
                }
            }
        }

        // Expand requested permission to include implied permissions
        Set<PermissionCode> satisfyingPermissions = findSatisfyingPermissions(request.getAction());
        Set<String> satisfyingCodes = satisfyingPermissions.stream().map(PermissionCode::code).collect(Collectors.toSet());

        List<RolePermissionScope> grants = rpsRepository.findByRoleIdIn(roleIds).stream()
                .filter(rps -> rps.getPermission() != null && satisfyingCodes.contains(rps.getPermission().getCode()))
                .collect(Collectors.toList());

        if (grants.isEmpty()) {
            return AuthorizationDecision.deny("RBAC", "User roles do not grant required permission");
        }

        // Resolve generic resource assignments if the role defines any limitations
        // For this phase, if we have a valid grant, we grant access.
        // Generic Resource Assignment limits what a user can do inside their scope.
        return evaluateScopeAndAssignment(request, grants);
    }

        private AuthorizationDecision evaluateScopeAndAssignment(AuthorizationRequest request, List<RolePermissionScope> grants) {
        ScopeType requiredScope = request.getRequiredScope(); // from request
        boolean requiresAssignment = com.example.taskflow.security.PermissionMetadataRegistry.requiresResourceAssignment(request.getAction().code());

        for (RolePermissionScope grant : grants) {
            ScopeType grantedScope = ScopeType.valueOf(grant.getScope().getCode());
            if (grantedScope.includes(requiredScope)) {
                
                if (!requiresAssignment) {
                    return AuthorizationDecision.allow("RBAC", "RBAC Role grants required global permission and scope");
                }
                
                List<com.example.taskflow.organization.rbac.domain.ResourceAssignment> assignments = resourceAssignmentRepository.findByRolePermissionScopeId(grant.getId());
                
                if (assignments.isEmpty()) {
                    continue; // This grant requires assignment but has none. Move to next grant.
                }

                Long targetId = null;
                String expectedResourceType = requiredScope.name();
                if (requiredScope == ScopeType.ORGANIZATION) {
                    targetId = extractOrganizationId(request);
                } else if (requiredScope == ScopeType.TEAM) {
                    targetId = extractIdFromContext(request, "teamId");
                } else if (requiredScope == ScopeType.PROJECT) {
                    targetId = extractIdFromContext(request, "projectId");
                } else if (requiredScope == ScopeType.OWN) {
                    targetId = extractIdFromContext(request, "userId"); 
                }

                if (targetId == null) {
                    // Fallback to direct resource if it matches the scope
                    if (requiredScope.name().equals(request.getResourceType())) {
                        targetId = request.getResourceId();
                    }
                }
                
                if (targetId != null) {
                    for (com.example.taskflow.organization.rbac.domain.ResourceAssignment ra : assignments) {
                        if (ra.getResourceType().equals(expectedResourceType) && ra.getResourceId().equals(targetId)) {
                            return AuthorizationDecision.allow("RBAC", "RBAC Role grants permission and matching resource assignment");
                        }
                    }
                }
                
                // No assignment matched, continue checking other grants.
            }
        }

        return AuthorizationDecision.deny("RBAC", "RBAC Role grants permission but insufficient scope or missing resource assignment");
    }

    private Long extractIdFromContext(AuthorizationRequest request, String key) {
        Object idObj = request.getContext().get(key);
        if (idObj instanceof Number number) {
            return org.springframework.util.NumberUtils.convertNumberToTargetClass(number, Long.class);
        }
        return null;
    }

    private Set<PermissionCode> findSatisfyingPermissions(PermissionCode required) {
        EnumSet<PermissionCode> satisfying = EnumSet.of(required);
        for (PermissionCode candidate : PermissionCode.values()) {
            if (candidate != required && PermissionImplications.implies(candidate, required)) {
                satisfying.add(candidate);
            }
        }
        return satisfying;
    }

    private Long extractOrganizationId(AuthorizationRequest request) {
        Object idObj = request.getContext().get("organizationId");
        if (idObj instanceof Number number) {
            return org.springframework.util.NumberUtils.convertNumberToTargetClass(number, Long.class);
        }
        return null;
    }
}
