package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.security.authorization.engine.MembershipResolver;
import com.example.taskflow.security.authorization.engine.OwnershipResolver;
import com.example.taskflow.security.authorization.engine.PolicyEvaluator;
import com.example.taskflow.security.authorization.engine.RBACAuthorizer;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationEngineImpl implements AuthorizationEngine {

    private final MembershipResolver membershipResolver;
    private final OwnershipResolver ownershipResolver;
    private final RBACAuthorizer rbacAuthorizer;
    private final PolicyEvaluator policyEvaluator;

    public AuthorizationEngineImpl(MembershipResolver membershipResolver,
                                   OwnershipResolver ownershipResolver,
                                   RBACAuthorizer rbacAuthorizer,
                                   PolicyEvaluator policyEvaluator) {
        this.membershipResolver = membershipResolver;
        this.ownershipResolver = ownershipResolver;
        this.rbacAuthorizer = rbacAuthorizer;
        this.policyEvaluator = policyEvaluator;
    }

    @Override
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        // Stage 1: Membership checks visibility
        AuthorizationDecision membershipDecision = membershipResolver.resolveMembership(request);
        if (membershipDecision.isDenied()) {
            return membershipDecision;
        }

        // If action is intrinsically granted by membership alone (e.g. basic visibility), short-circuit RBAC
        if (membershipDecision.isGranted()) {
            return enforcePolicies(request, membershipDecision);
        }

        // Stage 2: Ownership checks intrinsic resource assignments
        AuthorizationDecision ownershipDecision = ownershipResolver.resolveOwnership(request);
        if (ownershipDecision.isDenied()) {
            return ownershipDecision; // Hard deny (e.g., someone explicitly blocked from this action)
        }

        // If action is intrinsically granted by ownership alone, short-circuit RBAC
        if (ownershipDecision.isGranted()) {
            return enforcePolicies(request, ownershipDecision);
        }

        // Stage 3: RBAC explicit authorization
        AuthorizationDecision rbacDecision = rbacAuthorizer.authorize(request);
        if (rbacDecision.isDenied()) {
            return rbacDecision;
        }
        
        if (rbacDecision.isAbstain()) {
             return AuthorizationDecision.deny("ENGINE", "No applicable permissions granted");
        }

        // Stage 4: Business Policies
        return enforcePolicies(request, rbacDecision);
    }

    private AuthorizationDecision enforcePolicies(AuthorizationRequest request, AuthorizationDecision priorGrant) {
        AuthorizationDecision policyDecision = policyEvaluator.evaluatePolicies(request);
        if (policyDecision.isDenied()) {
            return policyDecision;
        }
        return priorGrant;
    }
}
