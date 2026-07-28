package com.example.taskflow.security.authorization;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;

/**
 * Registry of all available policy predicates.
 *
 * <p>Predicates are registered at application startup and looked up by key
 * during policy evaluation. The key matches the {@code policy_key} column
 * in the {@code permission_policies} table.
 *
 * <p>Built-in predicates are registered in the constructor. Additional
 * predicates can be added via {@link #register(String, PolicyPredicate)}.
 */
@Component
public class PolicyPredicateRegistry {

    private static final Logger log = LoggerFactory.getLogger(PolicyPredicateRegistry.class);

    private final Map<String, PolicyPredicate> predicates = new HashMap<>();

    public PolicyPredicateRegistry() {
        registerBuiltins();
    }

    /**
     * Registers a policy predicate by key.
     */
    public void register(String key, PolicyPredicate predicate) {
        predicates.put(key, predicate);
        log.debug("Registered policy predicate: {}", key);
    }

    /**
     * Retrieves a policy predicate by key.
     * Returns null if no predicate is registered for the given key.
     */
    public PolicyPredicate get(String key) {
        return predicates.get(key);
    }

    /**
     * Returns true if a predicate is registered for the given key.
     */
    public boolean contains(String key) {
        return predicates.containsKey(key);
    }

    private void registerBuiltins() {
        // â”€â”€ Ownership predicates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        register("IS_ASSIGNEE", (request, params) -> {
            Object assigneeId = request.getPolicyContext().get("assigneeId");
            return assigneeId != null && assigneeId.equals(request.getUser().getId());
        });

        register("IS_CREATOR", (request, params) -> {
            Object createdById = request.getPolicyContext().get("createdById");
            return createdById != null && createdById.equals(request.getUser().getId());
        });

        register("IS_REVIEWER", (request, params) -> {
            Object reviewerId = request.getPolicyContext().get("reviewerId");
            return reviewerId != null && reviewerId.equals(request.getUser().getId());
        });

        register("IS_TEAM_LEAD", (request, params) -> {
            Object isTeamLead = request.getPolicyContext().get("isTeamLead");
            return Boolean.TRUE.equals(isTeamLead);
        });

        register("IS_PROJECT_OWNER", (request, params) -> {
            Object projectOwnerId = request.getPolicyContext().get("projectOwnerId");
            return projectOwnerId != null && projectOwnerId.equals(request.getUser().getId());
        });

        register("IS_TEAM_MEMBER", (request, params) -> {
            Object isTeamMember = request.getPolicyContext().get("isTeamMember");
            return Boolean.TRUE.equals(isTeamMember);
        });

        register("IS_ORG_MEMBER", (request, params) -> {
            // If the pipeline reached this point, the user is already verified as an org member
            return true;
        });

        // â”€â”€ Resource state predicates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        register("RESOURCE_NOT_ARCHIVED", (request, params) -> {
            Object archived = request.getPolicyContext().get("archived");
            return !Boolean.TRUE.equals(archived);
        });

        register("RESOURCE_IS_ACTIVE", (request, params) -> {
            Object status = request.getPolicyContext().get("resourceStatus");
            return "ACTIVE".equals(status);
        });

        register("ORG_IS_ACTIVE", (request, params) -> {
            Object orgStatus = request.getPolicyContext().get("orgStatus");
            return orgStatus == null || "ACTIVE".equals(orgStatus);
        });

        // â”€â”€ Task status predicates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        register("TASK_STATUS_EQUALS", (request, params) -> {
            Object taskStatus = request.getPolicyContext().get("taskStatus");
            if (taskStatus == null || params == null) return false;
            // params is JSON like {"status": "IN_REVIEW"}
            return params.contains(taskStatus.toString());
        });

        register("TASK_STATUS_NOT_EQUALS", (request, params) -> {
            Object taskStatus = request.getPolicyContext().get("taskStatus");
            if (taskStatus == null || params == null) return true;
            return !params.contains(taskStatus.toString());
        });

        // â”€â”€ Guard predicates â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        register("NOT_SELF", (request, params) -> {
            Object targetUserId = request.getPolicyContext().get("targetUserId");
            return targetUserId == null || !targetUserId.equals(request.getUser().getId());
        });

        register("REVIEWER_OUTRANKS_ASSIGNEE", (request, params) -> {
            Object reviewerPriority = request.getPolicyContext().get("reviewerPriority");
            Object assigneePriority = request.getPolicyContext().get("assigneePriority");
            if (reviewerPriority instanceof Integer rp && assigneePriority instanceof Integer ap) {
                return rp < ap; // lower priority number = higher rank
            }
            return true; // default pass if priorities unavailable
        });

        register("NOT_LAST_ADMIN", (request, params) -> {
            Object adminCount = request.getPolicyContext().get("orgAdminCount");
            return adminCount instanceof Integer count && count > 1;
        });

        register("NOT_OBSERVER", (request, params) -> {
            Object isObserver = request.getPolicyContext().get("isObserver");
            return !Boolean.TRUE.equals(isObserver);
        });
    }
}