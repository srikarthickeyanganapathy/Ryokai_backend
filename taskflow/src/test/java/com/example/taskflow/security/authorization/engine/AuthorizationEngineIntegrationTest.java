package com.example.taskflow.security.authorization.engine;

import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.engine.impl.AuthorizationEngineImpl;
import com.example.taskflow.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the orchestration behavior of the AuthorizationEngine,
 * specifically ensuring that stages short-circuit correctly.
 */
class AuthorizationEngineIntegrationTest {

    private MembershipResolver membershipResolver;
    private OwnershipResolver ownershipResolver;
    private RBACAuthorizer rbacAuthorizer;
    private PolicyEvaluator policyEvaluator;
    private AuthorizationEngine engine;

    @BeforeEach
    void setUp() {
        membershipResolver = mock(MembershipResolver.class);
        ownershipResolver = mock(OwnershipResolver.class);
        rbacAuthorizer = mock(RBACAuthorizer.class);
        policyEvaluator = mock(PolicyEvaluator.class);

        engine = new AuthorizationEngineImpl(
                membershipResolver,
                ownershipResolver,
                rbacAuthorizer,
                policyEvaluator
        );
    }

    private AuthorizationRequest createMockRequest(PermissionCode permission) {
        User user = new User();
        user.setId(1L);
        return AuthorizationRequest.builder(user, permission)
                .context(java.util.Map.of("organizationId", 10L)).requiredScope(com.example.taskflow.security.ScopeType.ORGANIZATION)
                .resourceType("TASK")
                .resourceId(100L)
                .build();
    }

    @Test
    void testMembershipDeny_shortCircuitsEverything() {
        AuthorizationRequest req = createMockRequest(PermissionCode.TASK_VIEW);

        when(membershipResolver.resolveMembership(req))
                .thenReturn(AuthorizationDecision.deny("MEMBERSHIP", "User is not a member of this organization"));

        AuthorizationDecision decision = engine.authorize(req);

        assertTrue(decision.isDenied());
        assertEquals("User is not a member of this organization", decision.reason());

        // Verify short-circuiting
        verify(membershipResolver).resolveMembership(req);
        verifyNoInteractions(ownershipResolver);
        verifyNoInteractions(rbacAuthorizer);
        verifyNoInteractions(policyEvaluator);
    }

    @Test
    void testMembershipGrant_shortCircuitsOwnershipAndRBAC_butExecutesPolicies() {
        AuthorizationRequest req = createMockRequest(PermissionCode.TASK_VIEW);

        // e.g. Basic visibility that membership guarantees
        when(membershipResolver.resolveMembership(req))
                .thenReturn(AuthorizationDecision.grant("MEMBERSHIP"));
        when(policyEvaluator.evaluatePolicies(req))
                .thenReturn(AuthorizationDecision.grant("POLICIES_OK"));

        AuthorizationDecision decision = engine.authorize(req);

        assertTrue(decision.isGranted());

        InOrder inOrder = inOrder(membershipResolver, policyEvaluator);
        inOrder.verify(membershipResolver).resolveMembership(req);
        inOrder.verify(policyEvaluator).evaluatePolicies(req);

        verifyNoInteractions(ownershipResolver);
        verifyNoInteractions(rbacAuthorizer);
    }

    @Test
    void testOwnershipDeny_shortCircuitsRBACAndPolicies() {
        AuthorizationRequest req = createMockRequest(PermissionCode.TASK_UPDATE);

        when(membershipResolver.resolveMembership(req))
                .thenReturn(AuthorizationDecision.abstain("none"));
        
        when(ownershipResolver.resolveOwnership(req))
                .thenReturn(AuthorizationDecision.deny("OWNERSHIP_RESTRICTED", "User is explicitly blocked as owner"));

        AuthorizationDecision decision = engine.authorize(req);

        assertTrue(decision.isDenied());
        assertEquals("OWNERSHIP_RESTRICTED", decision.stage());
        assertEquals("User is explicitly blocked as owner", decision.reason());

        verify(membershipResolver).resolveMembership(req);
        verify(ownershipResolver).resolveOwnership(req);
        verifyNoInteractions(rbacAuthorizer);
        verifyNoInteractions(policyEvaluator);
    }

    @Test
    void testOwnershipGrant_shortCircuitsRBAC_butExecutesPolicies() {
        AuthorizationRequest req = createMockRequest(PermissionCode.TASK_UPDATE);

        when(membershipResolver.resolveMembership(req))
                .thenReturn(AuthorizationDecision.abstain("none"));
        
        when(ownershipResolver.resolveOwnership(req))
                .thenReturn(AuthorizationDecision.grant("OWNER"));

        when(policyEvaluator.evaluatePolicies(req))
                .thenReturn(AuthorizationDecision.grant("POLICIES_OK"));

        AuthorizationDecision decision = engine.authorize(req);

        assertTrue(decision.isGranted());

        InOrder inOrder = inOrder(membershipResolver, ownershipResolver, policyEvaluator);
        inOrder.verify(membershipResolver).resolveMembership(req);
        inOrder.verify(ownershipResolver).resolveOwnership(req);
        inOrder.verify(policyEvaluator).evaluatePolicies(req);

        verifyNoInteractions(rbacAuthorizer);
    }

    @Test
    void testRBACGrant_executesPolicies() {
        AuthorizationRequest req = createMockRequest(PermissionCode.TASK_UPDATE);

        when(membershipResolver.resolveMembership(req))
                .thenReturn(AuthorizationDecision.abstain("none"));
        when(ownershipResolver.resolveOwnership(req))
                .thenReturn(AuthorizationDecision.abstain("none"));
        when(rbacAuthorizer.authorize(req))
                .thenReturn(AuthorizationDecision.grant("RBAC_ROLE"));
        when(policyEvaluator.evaluatePolicies(req))
                .thenReturn(AuthorizationDecision.grant("POLICIES_OK"));

        AuthorizationDecision decision = engine.authorize(req);

        assertTrue(decision.isGranted());

        InOrder inOrder = inOrder(membershipResolver, ownershipResolver, rbacAuthorizer, policyEvaluator);
        inOrder.verify(membershipResolver).resolveMembership(req);
        inOrder.verify(ownershipResolver).resolveOwnership(req);
        inOrder.verify(rbacAuthorizer).authorize(req);
        inOrder.verify(policyEvaluator).evaluatePolicies(req);
    }

    @Test
    void testPolicyDeny_overridesRBACGrant() {
        AuthorizationRequest req = createMockRequest(PermissionCode.TASK_APPROVE);

        when(membershipResolver.resolveMembership(req))
                .thenReturn(AuthorizationDecision.abstain("none"));
        when(ownershipResolver.resolveOwnership(req))
                .thenReturn(AuthorizationDecision.abstain("none"));
        
        // RBAC says YES
        when(rbacAuthorizer.authorize(req))
                .thenReturn(AuthorizationDecision.grant("RBAC_ROLE"));
                
        // But Policy says NO (e.g. self-approval)
        when(policyEvaluator.evaluatePolicies(req))
                .thenReturn(AuthorizationDecision.deny("POLICY_VIOLATION", "Cannot approve own task"));

        AuthorizationDecision decision = engine.authorize(req);

        assertTrue(decision.isDenied());
        assertEquals("POLICY_VIOLATION", decision.stage());
        assertEquals("Cannot approve own task", decision.reason());

        verify(rbacAuthorizer).authorize(req);
        verify(policyEvaluator).evaluatePolicies(req);
    }
}
