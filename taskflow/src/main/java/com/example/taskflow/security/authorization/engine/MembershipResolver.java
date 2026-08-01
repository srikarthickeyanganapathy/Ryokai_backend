package com.example.taskflow.security.authorization.engine;

import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationDecision;

/**
 * Stage 1: Determines if the user has baseline visibility into the requested workspace.
 */
public interface MembershipResolver {
    AuthorizationDecision resolveMembership(AuthorizationRequest request);
}
