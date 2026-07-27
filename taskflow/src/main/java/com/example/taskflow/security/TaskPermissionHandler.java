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
import com.example.taskflow.service.PermissionService;
import com.example.taskflow.security.authorization.WorkspaceTypeResolver;
import com.example.taskflow.security.WorkspaceType;
import com.example.taskflow.security.authorization.LegacyPermissionMapper;
import com.example.taskflow.security.PermissionCode;

@Component
public class TaskPermissionHandler implements DomainPermissionHandler {

    private final TaskRepository taskRepository;
    private final TaskStrategyFactory taskStrategyFactory;
    private final OrganizationRepository organizationRepository;
    private final PermissionService permissionService;
    private final com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository;

    public TaskPermissionHandler(TaskRepository taskRepository,
                                 TaskStrategyFactory taskStrategyFactory,
                                 OrganizationRepository organizationRepository,
                                 PermissionService permissionService,
                                 com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository) {
        this.taskRepository = taskRepository;
        this.taskStrategyFactory = taskStrategyFactory;
        this.organizationRepository = organizationRepository;
        this.permissionService = permissionService;
        this.membershipRepository = membershipRepository;
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
        if (targetDomainObject instanceof Task task) {
            if (!isOrganizationActive(task.getOrg(), user)) return false;
            return hasPrivilege(user, task, permission);
        }
        if (targetDomainObject instanceof com.example.taskflow.dto.TaskRequestDTO dto) {
            return hasCreatePrivilege(user, dto, permission);
        }
        if (targetDomainObject instanceof com.example.taskflow.dto.BulkAssignRequestDTO) {
            if ("TASK_CREATE".equals(permission)) {
                // If creating without a specific mode, default to ORG pipeline
                PermissionCode code = LegacyPermissionMapper.resolveForDomain("TASK", permission);
                if (code != null && user != null) {
                    // For bulk assign without a task context, we can't reliably do an org check without orgId.
                    // This implies the endpoint needs to accept an orgId or the user must pass it.
                    // Assuming fallback to old logic for now just for this edge case.
                    return taskStrategyFactory.get(com.example.taskflow.domain.TaskMode.ORG).canCreate(user, null);
                }
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
            
            if (mode == com.example.taskflow.domain.TaskMode.ORG && request.getOrgId() != null) {
                PermissionCode code = LegacyPermissionMapper.resolveForDomain("TASK", permission);
                if (code != null) {
                    return permissionService.isAuthorized(user, code, request.getOrgId());
                }
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
            // Null tasks can't be resolved to an Org, fallback to legacy
            if ("ASSIGN".equals(permission) || "TASK_ASSIGN".equals(permission)) {
                return membershipRepository.findByUserId(user.getId()).stream()
                        .filter(m -> m.getOrgRole() != null)
                        .anyMatch(m -> m.getOrgRole().getRolePermissionScopes().stream()
                                .anyMatch(rps -> "TASK_ASSIGN".equals(rps.getPermission().getName())));
            }
            return false;
        }

        WorkspaceType type = WorkspaceTypeResolver.fromTask(task);
        
        // ── NEW: Route ORG workspaces to the RBAC pipeline ──
        if (type == WorkspaceType.ORGANIZATION) {
            PermissionCode code = LegacyPermissionMapper.resolveForDomain("TASK", permission);
            if (code != null) {
                return permissionService.isAuthorized(user, code, task.getOrg().getId(), "TASK", task.getId());
            }
            return false;
        }

        // ── LEGACY: Fallback for CREW and PERSONAL workspaces ──
        // (Observer veto only applied to ORG tasks, so it is omitted here)

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
