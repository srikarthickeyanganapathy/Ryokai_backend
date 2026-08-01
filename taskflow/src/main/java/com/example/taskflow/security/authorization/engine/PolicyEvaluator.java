package com.example.taskflow.security.authorization.engine;

import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationDecision;

/**
 * Stage 4: Enforces strict business invariants (e.g., self-approval prevention, minimum reviewer counts).
 */
public interface PolicyEvaluator {
    AuthorizationDecision evaluatePolicies(AuthorizationRequest request);
}
