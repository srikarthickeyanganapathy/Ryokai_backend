package com.example.taskflow.security;

import java.io.Serializable;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.service.PermissionService;
import org.springframework.security.core.userdetails.UserDetails;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final RoleStrategyFactory roleStrategyFactory;
    private final TaskRepository taskRepository;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public CustomPermissionEvaluator(RoleStrategyFactory roleStrategyFactory, TaskRepository taskRepository, PermissionService permissionService, UserRepository userRepository, ProjectRepository projectRepository) {
        this.roleStrategyFactory = roleStrategyFactory;
        this.taskRepository = taskRepository;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
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
            return hasPrivilege(user, (Task) targetDomainObject, perm);
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
            return switch (perm) {
                case "CREATE" -> true;   // any authenticated user
                case "EDIT", "DELETE" -> project != null
                    && project.getCreatedBy() != null
                    && project.getCreatedBy().getId().equals(user.getId());
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
            return hasPrivilege(user, task, perm);
        }

        return false;
    }

    private boolean hasPrivilege(User user, Task task, String permission) {
        // SUPER_ADMIN shortcut
        if (permissionService.hasPermission(user, "SUPER_ADMIN_OVERRIDE_CHECK")) {
            return true;
        }

        // Personal tasks: owner has full access
        if (task != null && task.isPersonal() && task.getOrganization() == null) {
            boolean isOwner = (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId())) ||
                              (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId()));
            if (isOwner) return true;
        }

        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);

        return switch (permission) {
            case "VIEW", "TASK_VIEW", "READ", "TASK_READ" -> strategy.canViewTask(user, task);
            case "REVIEW", "TASK_REVIEW" -> strategy.canReview(user, task);
            case "ASSIGN", "TASK_ASSIGN" -> strategy.canAssign(user);
            case "EDIT", "TASK_EDIT" -> strategy.canEdit(user, task);
            case "DELETE", "TASK_DELETE" -> strategy.canDelete(user, task);
            case "REASSIGN", "TASK_REASSIGN" -> strategy.canReassign(user, task);
            case "COMMENT", "TASK_COMMENT" -> {
                // Personal tasks: only creator can comment
                if (task != null && task.isPersonal() && task.getOrganization() == null) {
                    boolean isCreator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
                    yield isCreator;
                }
                yield strategy.canViewTask(user, task);
            }
            case "CHECKLIST_EDIT", "TASK_CHECKLIST_EDIT" -> strategy.canEdit(user, task);
            case "DEPENDENCY_EDIT", "TASK_DEPENDENCY_EDIT" -> strategy.canEdit(user, task);
            case "ARCHIVE", "TASK_ARCHIVE" -> strategy.canDelete(user, task); // Fallback to delete logic for now
            default -> false;
        };
    }
}
