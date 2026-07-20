package com.example.taskflow.security;

import java.io.Serializable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.strategy.task.TaskStrategyFactory;
import com.example.taskflow.strategy.task.Approvable;

@Component
public class TaskPermissionHandler implements DomainPermissionHandler {

    private final RoleStrategyFactory roleStrategyFactory;
    private final TaskRepository taskRepository;
    private final TaskStrategyFactory taskStrategyFactory;
    private final OrganizationRepository organizationRepository;

    public TaskPermissionHandler(RoleStrategyFactory roleStrategyFactory,
                                 TaskRepository taskRepository,
                                 TaskStrategyFactory taskStrategyFactory,
                                 OrganizationRepository organizationRepository) {
        this.roleStrategyFactory = roleStrategyFactory;
        this.taskRepository = taskRepository;
        this.taskStrategyFactory = taskStrategyFactory;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public String getTargetType() {
        return "Task";
    }

    private boolean isOrganizationActive(Organization org, User user) {
        if (org == null || org.getId() == null) return true;
        if (user != null && user.isSuperAdmin()) return true;
        Organization freshOrg = organizationRepository.findById(org.getId()).orElse(null);
        return freshOrg != null && freshOrg.getStatus() == Organization.OrgStatus.ACTIVE;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Object targetDomainObject, String permission) {
        if (targetDomainObject instanceof Task) {
            Task task = (Task) targetDomainObject;
            if (!isOrganizationActive(task.getOrg(), user)) return false;
            return hasPrivilege(user, task, permission);
        }
        if (targetDomainObject instanceof com.example.taskflow.dto.TaskRequestDTO) {
            return hasCreatePrivilege(user, (com.example.taskflow.dto.TaskRequestDTO) targetDomainObject, permission);
        }
        if (targetDomainObject instanceof com.example.taskflow.dto.BulkAssignRequestDTO) {
            if ("TASK_CREATE".equals(permission)) {
                return taskStrategyFactory.get(com.example.taskflow.domain.TaskMode.ORG).canCreate(user, null);
            }
        }
        return false;
    }

    private boolean hasCreatePrivilege(User user, com.example.taskflow.dto.TaskRequestDTO request, String permission) {
        if ("TASK_CREATE".equals(permission)) {
            com.example.taskflow.domain.TaskMode mode = com.example.taskflow.domain.TaskMode.ORG;
            if (request.isPersonal()) {
                mode = com.example.taskflow.domain.TaskMode.PERSONAL;
            } else if (request.getCrewId() != null) {
                mode = com.example.taskflow.domain.TaskMode.CREW;
            }
            return taskStrategyFactory.get(mode).canCreate(user, request);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission) {
        if (targetId == null) {
            return hasPrivilege(user, null, permission);
        }
        
        Long taskId;
        try {
            taskId = ((Number) targetId).longValue();
        } catch (ClassCastException e) {
            return false;
        }

        org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        Task task = null;
        if (attrs != null) {
            task = (Task) attrs.getAttribute("CACHED_TASK_" + taskId, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }

        if (task == null) {
            task = taskRepository.findById(taskId).orElse(null);
            if (task != null && attrs != null) {
                attrs.setAttribute("CACHED_TASK_" + taskId, task, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            }
        }

        if (task == null) return false;
        if (!isOrganizationActive(task.getOrg(), user)) return false;
        return hasPrivilege(user, task, permission);
    }

    private boolean hasPrivilege(User user, Task task, String permission) {
        if (task == null) {
            RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
            return switch (permission) {
                case "ASSIGN", "TASK_ASSIGN" -> strategy.canAssign(user);
                default -> false;
            };
        }

        if (task != null && !isOrganizationActive(task.getOrg(), user)) {
            return false;
        }

        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
        
        if (task != null && strategy.isObserverVeto(user, task)) {
            switch (permission) {
                case "EDIT", "TASK_EDIT", "CHECKLIST_EDIT", 
                     "DELETE", "TASK_DELETE", 
                     "REASSIGN", "TASK_REASSIGN", 
                     "DEPENDENCY_EDIT", "TASK_DEPENDENCY_EDIT", 
                     "EVIDENCE_EDIT", "TASK_EVIDENCE_EDIT", 
                     "ARCHIVE", "TASK_ARCHIVE",
                     "REVIEW", "TASK_REVIEW":
                    return false;
            }
        }

        return switch (permission) {
            case "VIEW", "TASK_VIEW", "READ", "TASK_READ", "COMMENT" -> taskStrategyFactory.get(task).canView(user, task);
            case "REVIEW", "TASK_REVIEW" -> {
                if (!(taskStrategyFactory.get(task) instanceof Approvable a)) yield false;
                yield a.canApprove(user, task);
            }
            case "EDIT", "TASK_EDIT", "CHECKLIST_EDIT" -> taskStrategyFactory.get(task).canEdit(user, task);
            case "DELETE", "TASK_DELETE" -> taskStrategyFactory.get(task).canDelete(user, task);
            case "REASSIGN", "TASK_REASSIGN" -> taskStrategyFactory.get(task).canReassign(user, task);
            case "DEPENDENCY_EDIT", "TASK_DEPENDENCY_EDIT" -> taskStrategyFactory.get(task).canEditDependency(user, task);
            case "EVIDENCE_EDIT", "TASK_EVIDENCE_EDIT" -> taskStrategyFactory.get(task).canEdit(user, task);
            case "ARCHIVE", "TASK_ARCHIVE" -> taskStrategyFactory.get(task).canArchive(user, task);
            default -> false;
        };
    }
}
