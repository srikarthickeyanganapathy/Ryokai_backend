package com.example.taskflow.security;

import java.io.Serializable;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.service.PermissionService;
import org.springframework.security.core.userdetails.UserDetails;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final RoleStrategyFactory roleStrategyFactory;
    private final TaskRepository taskRepository;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;

    public CustomPermissionEvaluator(RoleStrategyFactory roleStrategyFactory, TaskRepository taskRepository, PermissionService permissionService, UserRepository userRepository, ProjectRepository projectRepository, OrganizationMembershipRepository membershipRepository, OrganizationRepository organizationRepository, TeamMemberRepository teamMemberRepository, CrewMemberRepository crewMemberRepository) {
        this.roleStrategyFactory = roleStrategyFactory;
        this.taskRepository = taskRepository;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.crewMemberRepository = crewMemberRepository;
    }

    private User getUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        String username = null;
        if (auth.getPrincipal() instanceof UserDetails) {
            username = ((UserDetails) auth.getPrincipal()).getUsername();
        } else if (auth.getPrincipal() instanceof String) {
            username = (String) auth.getPrincipal();
        }
        if (username == null) return null;

        org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            User cachedUser = (User) attrs.getAttribute("CACHED_USER_" + username, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (cachedUser != null) {
                return cachedUser;
            }
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && attrs != null) {
            attrs.setAttribute("CACHED_USER_" + username, user, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
        return user;
    }

    /**
     * Checks whether the given organization is active (not SUSPENDED or DELETED).
     * Super Admins are exempt  -  they operate at the platform level, not within orgs.
     */
    private boolean isOrganizationActive(Organization org, User user) {
        if (org == null || org.getId() == null) return true; // personal / non-org resource  -  no org check needed
        if (isSuperAdmin(user)) return true; // Super Admin manages orgs at platform level
        
        Organization freshOrg = organizationRepository.findById(org.getId()).orElse(null);
        return freshOrg != null && freshOrg.getStatus() == Organization.OrgStatus.ACTIVE;
    }

    private boolean isSuperAdmin(User user) {
        if (user == null || user.getRoles() == null) return false;
        return user.getRoles().stream()
                .anyMatch(r -> {
                    String name = r.getName();
                    if (name.startsWith("ROLE_")) name = name.substring(5);
                    return "SUPER_ADMIN".equals(name);
                });
    }

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if ((auth == null) || !(permission instanceof String)){
            return false;
        }
        User user = getUser(auth);
        if (user == null) return false;
        
        String perm = (String) permission;

        // Domain-scoped permission check
        if (targetDomainObject instanceof Task) {
            Task task = (Task) targetDomainObject;
            if (!isOrganizationActive(task.getOrg(), user)) return false;
            return hasPrivilege(user, task, perm);
        }
        
        // General non-domain-scoped check (e.g. method level security like @PreAuthorize("hasPermission(null, 'USER_MANAGE')"))
        if (targetDomainObject == null) {
            return permissionService.hasPermission(user, perm);
        }
        
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        if ((auth == null) || (targetType == null) || !(permission instanceof String)) {
            return false;
        }

        User user = getUser(auth);
        if (user == null) return false;

        String perm = (String) permission;

        if ("Project".equals(targetType)) {
            Project project = targetId == null ? null
                : projectRepository.findById(((Number) targetId).longValue()).orElse(null);
            // Block operations on projects belonging to suspended/deleted orgs
            if (project != null && !isOrganizationActive(project.getOrganization(), user)) {
                return false;
            }
            return switch (perm) {
                case "CREATE" -> true;   // any authenticated user (PROJECT_CREATE is enforced at the service level)
                case "READ" -> project != null && (
                    (project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId()))
                    || (project.getOrganization() != null
                        && membershipRepository.existsByUserAndOrganization(user, project.getOrganization()))
                    || (project.getTeam() != null
                        && teamMemberRepository.existsByIdTeamIdAndIdUserId(project.getTeam().getId(), user.getId()))
                    || projectRepository.isProjectSharedWithUser(project.getId(), user.getId())
                    || permissionService.hasPermission(user, "SUPER_ADMIN_OVERRIDE_CHECK")
                );
                case "EDIT", "DELETE" -> project != null && (
                    (project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId()))
                    || (project.getOrganization() != null 
                        && membershipRepository.existsByUserAndOrganization(user, project.getOrganization())
                        && permissionService.hasPermission(user, "PROJECT_MANAGE"))
                );
                default -> false;
            };
        }

        if (targetId == null) {
            if ("Task".equals(targetType)) {
                return hasPrivilege(user, null, perm);
            }
            return permissionService.hasPermission(user, perm);
        }

        if ("Task".equals(targetType)) {
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
            // Block operations on tasks belonging to suspended/deleted orgs
            if (!isOrganizationActive(task.getOrg(), user)) return false;
            return hasPrivilege(user, task, perm);
        }

        return false;
    }

    private boolean hasPrivilege(User user, Task task, String permission) {
        // Personal tasks: owner has full access (no RBAC in personal mode)
        if (task != null && task.isPersonal() && task.getOrg() == null) {
            boolean isOwner = (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) ||
                              (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId()));
            if (isOwner) return true;
        }

        // Org suspension check  -  reject if the task's org is not active
        if (task != null && !isOrganizationActive(task.getOrg(), user)) {
            return false;
        }

        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);
        
        // Bug #4 Fix: Centralize Observer Veto check for mutating permissions
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
            case "VIEW", "TASK_VIEW", "READ", "TASK_READ", "COMMENT" -> strategy.canViewTask(user, task);
            case "REVIEW", "TASK_REVIEW" -> strategy.canReview(user, task);
            case "ASSIGN", "TASK_ASSIGN" -> strategy.canAssign(user);
            case "EDIT", "TASK_EDIT", "CHECKLIST_EDIT" -> strategy.canEdit(user, task);
            case "DELETE", "TASK_DELETE" -> strategy.canDelete(user, task);
            case "REASSIGN", "TASK_REASSIGN" -> strategy.canReassign(user, task);
            case "DEPENDENCY_EDIT", "TASK_DEPENDENCY_EDIT" -> strategy.canEditDependency(user, task);
            // Evidence uses EDIT privilege (adder / task editor)
            case "EVIDENCE_EDIT", "TASK_EVIDENCE_EDIT" -> strategy.canEdit(user, task);
            // RB-M04 fix: route ARCHIVE to the new dedicated canArchive method
            // (was routed to canDelete, but controller used 'EDIT'  -  making this
            // branch dead code. TaskController.toggleArchive now uses 'ARCHIVE'.)
            case "ARCHIVE", "TASK_ARCHIVE" -> strategy.canArchive(user, task);
            default -> false;
        };
    }
}
