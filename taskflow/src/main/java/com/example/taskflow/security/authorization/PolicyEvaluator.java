package com.example.taskflow.security.authorization;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.taskflow.organization.rbac.domain.PermissionPolicyMapping;
import com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionPolicyRepository;

/**
 * Evaluates runtime policy predicates for a given permission.
 *
 * <p>Policies are boolean predicates that must pass <b>after</b> permission and scope
 * checks succeed. They encode business rules like "reviewer must outrank assignee"
 * or "task must be in IN_REVIEW status."
 *
 * <p>Policy predicates are registered in the {@link PolicyPredicateRegistry} and
 * configured in the {@code permission_policies} table.
 */
@Service
public class PolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluator.class);

    private final PermissionPolicyRepository policyRepository;
    private final PolicyPredicateRegistry predicateRegistry;

    public PolicyEvaluator(PermissionPolicyRepository policyRepository,
                           PolicyPredicateRegistry predicateRegistry) {
        this.policyRepository = policyRepository;
        this.predicateRegistry = predicateRegistry;
    }

    /**
     * Evaluates all policies configured for the requested permission.
     *
     * @return GRANT if all policies pass, DENY with the failing policy key if any fail
     */
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        String permissionCode = request.getPermission().code();
        List<PermissionPolicyMapping> policies = policyRepository
                .findByPermissionCodeOrdered(permissionCode);

        if (policies.isEmpty()) {
            return AuthorizationDecision.grant("POLICY");
        }

        for (PermissionPolicyMapping policy : policies) {
            String key = policy.getPolicyKey();
            PolicyPredicate predicate = predicateRegistry.get(key);

            if (predicate == null) {
                log.warn("Unknown policy predicate '{}' for permission '{}'. Treating as pass.",
                        key, permissionCode);
                continue;
            }

            boolean result;
            try {
                result = predicate.evaluate(request, policy.getPolicyParams());
            } catch (Exception e) {
                log.error("Policy predicate '{}' threw exception for permission '{}': {}",
                        key, permissionCode, e.getMessage());
                // Policy evaluation failure = deny (fail-closed)
                return AuthorizationDecision.denyPolicy(key + " (error: " + e.getMessage() + ")");
            }

            if (!result) {
                if (policy.getIsRequired() && "AND".equals(policy.getOperator())) {
                    return AuthorizationDecision.denyPolicy(key);
                }
            }
        }

        return AuthorizationDecision.grant("POLICY");
    }
}