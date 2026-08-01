package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.OwnershipRole;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Registry mapping actions to the intrinsic resource assignments that permit them.
 * This encapsulates domain ownership rules without coupling the engine to domain entities.
 */
public class OwnershipActionRegistry {

    private static final Map<PermissionCode, Set<OwnershipRole>> REGISTRY = new EnumMap<>(PermissionCode.class);

    static {
        // --- Task Ownership Rights ---
        // Workflow execution (assignee)
        REGISTRY.put(PermissionCode.TASK_SUBMIT, EnumSet.of(OwnershipRole.ASSIGNEE));
        REGISTRY.put(PermissionCode.TASK_COMPLETE, EnumSet.of(OwnershipRole.ASSIGNEE));
        REGISTRY.put(PermissionCode.TASK_RECALL, EnumSet.of(OwnershipRole.ASSIGNEE));
        // Basic execution & updates
        REGISTRY.put(PermissionCode.TASK_UPDATE, EnumSet.of(OwnershipRole.ASSIGNEE, OwnershipRole.CREATOR));
        REGISTRY.put(PermissionCode.TASK_DEPENDENCY_UPDATE, EnumSet.of(OwnershipRole.CREATOR));
        REGISTRY.put(PermissionCode.TASK_DELETE, EnumSet.of(OwnershipRole.CREATOR));
        REGISTRY.put(PermissionCode.TASK_ARCHIVE, EnumSet.of(OwnershipRole.CREATOR));
        
        // Visibility
        REGISTRY.put(PermissionCode.TASK_VIEW, EnumSet.of(OwnershipRole.ASSIGNEE, OwnershipRole.CREATOR, OwnershipRole.PROJECT_OWNER, OwnershipRole.REVIEWER));
        
        // --- Project Ownership Rights ---
        REGISTRY.put(PermissionCode.PROJECT_VIEW, EnumSet.of(OwnershipRole.CREATOR, OwnershipRole.PROJECT_OWNER));
        REGISTRY.put(PermissionCode.PROJECT_UPDATE, EnumSet.of(OwnershipRole.CREATOR));
        REGISTRY.put(PermissionCode.PROJECT_DELETE, EnumSet.of(OwnershipRole.CREATOR));
    }

    /**
     * Returns the set of resource assignments that inherently grant the specified action.
     * If the action is strictly an RBAC-only administrative action, it returns an empty set.
     */
    public static Set<OwnershipRole> getRequiredAssignments(PermissionCode action) {
        return REGISTRY.getOrDefault(action, EnumSet.noneOf(OwnershipRole.class));
    }
}
