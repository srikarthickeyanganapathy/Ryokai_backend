package com.example.taskflow.security.authorization;

/**
 * A single policy predicate â€” a boolean function evaluated at runtime.
 *
 * <p>Implementations are registered in {@link PolicyPredicateRegistry} by key.
 * Each predicate is a stateless evaluator that receives the full
 * {@link AuthorizationRequest} context and optional JSON parameters
 * from the {@code permission_policies} table.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code IS_ASSIGNEE} â€” checks if user is the task assignee</li>
 *   <li>{@code RESOURCE_NOT_ARCHIVED} â€” checks if the resource is not archived</li>
 *   <li>{@code TASK_STATUS_EQUALS} â€” checks if task status matches a parameter</li>
 * </ul>
 */
@FunctionalInterface
public interface PolicyPredicate {

    /**
     * Evaluates this policy predicate.
     *
     * @param request the authorization request with full context
     * @param params  optional JSON parameters from the policy configuration (may be null)
     * @return true if the policy is satisfied, false if it should deny
     */
    boolean evaluate(AuthorizationRequest request, String params);
}