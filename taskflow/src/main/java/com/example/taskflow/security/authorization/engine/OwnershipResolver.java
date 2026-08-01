package com.example.taskflow.security.authorization.engine;

import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationDecision;

/**
 * Stage 2: Determines if the user is explicitly assigned to or owns the target resource,
 * granting them personal workflow execution rights.
 */
public interface OwnershipResolver {
    AuthorizationDecision resolveOwnership(AuthorizationRequest request);
}
