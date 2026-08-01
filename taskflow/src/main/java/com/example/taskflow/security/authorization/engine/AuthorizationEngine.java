package com.example.taskflow.security.authorization.engine;

import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationDecision;

/**
 * The unified entry point for all authorization decisions in the system.
 * Orchestrates Membership, Ownership, RBAC, and Business Policies.
 */
public interface AuthorizationEngine {
    AuthorizationDecision authorize(AuthorizationRequest request);
}
