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
    private final com.example.taskflow.repository.TeamRepository teamRepository;
    private final com.example.taskflow.repository.ProjectRepository projectRepository;

    public TaskPermissionHandler(TaskRepository taskRepository,
                                 TaskStrategyFactory taskStrategyFactory,
                                 OrganizationRepository organizationRepository,
                                 PermissionService permissionService,
                                 com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository,
                                 com.example.taskflow.repository.TeamRepository teamRepository,
                                 com.example.taskflow.repository.ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.taskStrategyFactory = taskStrategyFactory;
        this.organizationRepository = organizationRepository;
        this.permissionService = permissionService;
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
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

    private Long resolveOrgId(User user, com.example.taskflow.dto.TaskRequestDTO request) {
        if (request.getOrgId() != null) {
            return request.getOrgId();
        }
        if (request.getTeamId() != null) {
            var team = teamRepository.findById(request.getTeamId()).orElse(null);
            if (team != null && team.getOrganization() != null) {
                return team.getOrganization().getId();
            }
        }
        if (request.getProjectId() != null) {
            var project = projectRepository.findById(request.getProjectId()).orElse(null);
            if (project != null && project.getOrganization() != null) {
                return project.getOrganization().getId();
            }
        }
        if (user != null) {
            var memberships = membershipRepository.findByUserId(user.getId());
            if (!memberships.isEmpty()) {
                return memberships.get(0).getOrganization().getId();
            }
        }
        return null;
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
        if (targetDomainObject instanceof com.example.taskflow.dto.BulkAssignRequestDTO dto) {
            if ("TASK_CREATE".equals(permission) || "CREATE".equals(permission)) {
                Long orgId = dto.getTeamId() != null ? teamRepository.findById(dto.getTeamId()).map(t -> t.getOrganization() != null ? t.getOrganization().getId() : null).orElse(null) : null;
                if (orgId == null && user != null) {
                    var memberships = membershipRepository.findByUserId(user.getId());
                    if (!memberships.isEmpty()) {
                        orgId = memberships.get(0).getOrganization().getId();
                    }
                }
                if (orgId != null) {
                    return permissionService.isAuthorized(user, PermissionCode.TASK_CREATE, orgId);
                }
            }
        }
        return false;
    }

    private boolean hasCreatePrivilege(User user, com.example.taskflow.dto.TaskRequestDTO request, String permission) {
        if ("TASK_CREATE".equals(permission) || "CREATE".equals(permission)) {
            com.example.taskflow.domain.TaskMode mode = com.example.taskflow.domain.TaskMode.ORG;
            if (request.isPersonal()) {
                mode = com.example.taskflow.domain.TaskMode.PERSONAL;
            } else if (request.getCrewId() != null) {
                mode = com.example.taskflow.domain.TaskMode.CREW;
            }
            
            if (mode == com.example.taskflow.domain.TaskMode.ORG) {
                Long orgId = resolveOrgId(user, request);
                if (orgId != null) {
                    PermissionCode code = LegacyPermissionMapper.resolveForDomain("TASK", permission);
                    if (code != null) {
                        return permissionService.isAuthorized(user, code, orgId);
                    }
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
        if (user == null) return false;

        if (task == null) {
            // Null tasks: resolve org for user and check via RBAC pipeline
            if ("ASSIGN".equals(permission) || "TASK_ASSIGN".equals(permission)) {
                var memberships = membershipRepository.findByUserId(user.getId());
                if (!memberships.isEmpty()) {
                    Long orgId = memberships.get(0).getOrganization().getId();
                    return permissionService.isAuthorized(user, PermissionCode.TASK_ASSIGN, orgId);
                }
            }
            return false;
        }

        if (user.isSuperAdmin()) return true;

        WorkspaceType type = WorkspaceTypeResolver.fromTask(task);

        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        boolean isAssignor = task.getCreator() != null && task.getCreator().getId().equals(user.getId());

        // ── 1. CREW and PERSONAL Workspaces ───────────────────────
        if (type == WorkspaceType.PERSONAL || type == WorkspaceType.CREW) {
            return switch (permission) {
                case "SUBMIT", "TASK_SUBMIT", "COMPLETE", "RECALL", "TASK_RECALL" -> isAssignee;
                case "VIEW", "TASK_VIEW", "READ", "TASK_READ", "COMMENT" -> taskStrategyFactory.get(task).canView(user, task);
                case "REVIEW", "TASK_REVIEW" -> {
                    if (isAssignee) yield false;
                    if (isAssignor) yield true;
                    if (!(taskStrategyFactory.get(task) instanceof Approvable a)) yield false;
                    yield a.canApprove(user, task);
                }
                case "EDIT", "TASK_EDIT", "CHECKLIST_EDIT" -> taskStrategyFactory.get(task).canEdit(user, task);
                case "DELETE", "TASK_DELETE" -> taskStrategyFactory.get(task).canDelete(user, task);
                case "REASSIGN", "TASK_REASSIGN" -> isAssignor || taskStrategyFactory.get(task).canReassign(user, task);
                case "DEPENDENCY_EDIT", "TASK_DEPENDENCY_EDIT" -> taskStrategyFactory.get(task).canEditDependency(user, task);
                case "EVIDENCE_EDIT", "TASK_EVIDENCE_EDIT" -> isAssignee || taskStrategyFactory.get(task).canEdit(user, task);
                case "ARCHIVE", "TASK_ARCHIVE" -> taskStrategyFactory.get(task).canArchive(user, task);
                default -> false;
            };
        }

        // ── 2. ORGANIZATION Workspace ──────────────────────────────
        Long orgId = task.getOrg() != null ? task.getOrg().getId() : null;
        if (orgId == null) return false;

        PermissionCode code = LegacyPermissionMapper.resolveForDomain("TASK", permission);

        switch (permission) {
            // Rule 1 & Rule 2: Submit, Complete, and Recall can ONLY be done by the assignee. No other person, permissions don't matter here.
            case "SUBMIT", "TASK_SUBMIT", "COMPLETE", "RECALL", "TASK_RECALL" -> {
                return isAssignee;
            }

            // Rule 3: Rejection or Approval should be done by who has permission AND assignor also has permission. Assignee cannot self-approve.
            case "REVIEW", "TASK_REVIEW", "APPROVE", "REJECT" -> {
                if (isAssignee) return false;
                if (isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                if (taskStrategyFactory.get(task) instanceof Approvable a) {
                    return a.canApprove(user, task);
                }
                return false;
            }

            // Rule 4: Reassign follows the same approve or rejection concept (assignor has permission OR user has RBAC permission).
            case "REASSIGN", "TASK_REASSIGN" -> {
                if (isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canReassign(user, task);
            }

            case "VIEW", "TASK_VIEW", "READ", "TASK_READ", "COMMENT" -> {
                if (isAssignee || isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canView(user, task);
            }

            case "EDIT", "TASK_EDIT", "CHECKLIST_EDIT" -> {
                if (isAssignee || isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canEdit(user, task);
            }

            case "DELETE", "TASK_DELETE" -> {
                if (isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canDelete(user, task);
            }

            case "DEPENDENCY_EDIT", "TASK_DEPENDENCY_EDIT" -> {
                if (isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canEditDependency(user, task);
            }

            case "EVIDENCE_EDIT", "TASK_EVIDENCE_EDIT" -> {
                if (isAssignee || isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canEdit(user, task);
            }

            case "ARCHIVE", "TASK_ARCHIVE" -> {
                if (isAssignor) return true;
                if (code != null && permissionService.isAuthorized(user, code, orgId, "TASK", task.getId())) return true;
                return taskStrategyFactory.get(task).canArchive(user, task);
            }

            default -> {
                if (code != null) {
                    return permissionService.isAuthorized(user, code, orgId, "TASK", task.getId());
                }
                return false;
            }
        }
    }
}
