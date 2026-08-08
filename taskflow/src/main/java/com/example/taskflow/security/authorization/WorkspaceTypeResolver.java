package com.example.taskflow.security.authorization;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskMode;
import com.example.taskflow.security.WorkspaceType;

/**
 * Resolves the workspace type for a given context, determining which
 * authorization path to take.
 *
 * <p>The workspace type is determined by the resource being accessed:
 * <ul>
 *   <li>{@code PERSONAL} â€” No RBAC; owner-only access</li>
 *   <li>{@code CREW} â€” Fixed roles; no permission DB</li>
 *   <li>{@code ORGANIZATION} â€” Full RBAC pipeline</li>
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

        TaskMode mode = task.getMode();
        if (mode == TaskMode.PERSONAL) {
            return WorkspaceType.PERSONAL;
        }
        if (mode == TaskMode.CREW) {
            return WorkspaceType.CREW;
        }
        if (mode == TaskMode.ORG) {
            return WorkspaceType.ORGANIZATION;
        }
        // mode is null — task has no org, no crew, and isPersonal=false.
        // This indicates a data integrity issue. Fall back to PERSONAL as the
        // safe default (least privilege) rather than ORGANIZATION which would
        // trigger a membership deny when organizationId is absent from context.
        return WorkspaceType.PERSONAL;
    }

    /**
     * Resolves workspace type from a target domain object.
     * Returns ORGANIZATION as default for non-task entities.
     */
    public static WorkspaceType fromDomainObject(Object target) {
        if (target instanceof Task task) {
            return fromTask(task);
        }
        if (target instanceof com.example.taskflow.project.domain.Project project) {
            if (project.getOrganization() != null) return WorkspaceType.ORGANIZATION;
            if (project.getCrew() != null) return WorkspaceType.CREW;
            return WorkspaceType.PERSONAL;
        }
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