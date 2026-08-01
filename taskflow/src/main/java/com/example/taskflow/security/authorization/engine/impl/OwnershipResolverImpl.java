package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.OwnershipRole;
import com.example.taskflow.security.authorization.engine.OwnershipResolver;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
public class OwnershipResolverImpl implements OwnershipResolver {

    @Override
    public AuthorizationDecision resolveOwnership(AuthorizationRequest request) {
        if (request.getUser() == null || request.getUser().getId() == null) {
            return AuthorizationDecision.deny("OWNERSHIP", "Anonymous access is not allowed");
        }

        if (request.getUser().isSuperAdmin()) {
            return AuthorizationDecision.allow("OWNERSHIP", "SuperAdmin bypass");
        }

        Set<OwnershipRole> userAssignments = request.getOwnership();
        if (userAssignments == null || userAssignments.isEmpty()) {
            return AuthorizationDecision.abstain("User has no intrinsic assignments on this resource");
        }

        Set<OwnershipRole> requiredAssignments = OwnershipActionRegistry.getRequiredAssignments(request.getAction());
        if (requiredAssignments.isEmpty()) {
            return AuthorizationDecision.abstain("Action is not inherently granted by intrinsic ownership (requires RBAC)");
        }

        boolean hasOverlap = !Collections.disjoint(userAssignments, requiredAssignments);

        if (hasOverlap) {
            return AuthorizationDecision.allow("OWNERSHIP", "User holds required intrinsic assignment for personal workflow");
        }

        return AuthorizationDecision.abstain("User lacks required intrinsic assignment for this specific action");
    }
}
