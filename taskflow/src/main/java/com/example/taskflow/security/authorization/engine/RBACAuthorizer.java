package com.example.taskflow.security.authorization.engine;

import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationDecision;

/**
 * Stage 3: Evaluates explicit permissions, scopes, and assignments for cross-boundary or administrative actions.
 */
public interface RBACAuthorizer {
    AuthorizationDecision authorize(AuthorizationRequest request);
}
