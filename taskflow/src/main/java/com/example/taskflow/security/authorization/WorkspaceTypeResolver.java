package com.example.taskflow.security.authorization;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskMode;
import com.example.taskflow.security.WorkspaceType;

/**
 * Resolves the workspace type for a given context, determining which
 * authorization path to take.
 *
 * <p>The workspace type is determined by the resource being accessed:
 * <ul>
 *   <li>{@code PERSONAL} — No RBAC; owner-only access</li>
 *   <li>{@code CREW} — Fixed roles; no permission DB</li>
 *   <li>{@code ORGANIZATION} — Full RBAC pipeline</li>
 * </ul>
 */
public final class WorkspaceTypeResolver {

    private WorkspaceTypeResolver() {
        // utility class
    }

    /**
     * Resolves workspace type from a Task entity.
     */
    public static WorkspaceType fromTask(Task task) {
        if (task == null) return WorkspaceType.ORGANIZATION;

        if (task.getMode() == TaskMode.PERSONAL) {
            return WorkspaceType.PERSONAL;
        }
        if (task.getMode() == TaskMode.CREW) {
            return WorkspaceType.CREW;
        }
        return WorkspaceType.ORGANIZATION;
    }

    /**
     * Resolves workspace type from a target domain object.
     * Returns ORGANIZATION as default for non-task entities.
     */
    public static WorkspaceType fromDomainObject(Object target) {
        if (target instanceof Task task) {
            return fromTask(task);
        }
        // Projects, organizations, teams, goals, etc. are always org-scoped
        return WorkspaceType.ORGANIZATION;
    }

    /**
     * Resolves workspace type from explicit identifiers.
     */
    public static WorkspaceType fromContext(Long orgId, Long crewId, boolean isPersonal) {
        if (isPersonal) return WorkspaceType.PERSONAL;
        if (crewId != null) return WorkspaceType.CREW;
        if (orgId != null) return WorkspaceType.ORGANIZATION;
        return WorkspaceType.PERSONAL;
    }
}
