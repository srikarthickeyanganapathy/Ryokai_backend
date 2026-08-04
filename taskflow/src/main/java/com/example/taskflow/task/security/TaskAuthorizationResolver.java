package com.example.taskflow.task.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.PermissionMetadataRegistry;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.WorkspaceType;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationRequestBuilder;
import com.example.taskflow.security.authorization.OwnershipRole;
import com.example.taskflow.security.authorization.WorkspaceTypeResolver;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.user.domain.User;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class TaskAuthorizationResolver implements AuthorizationResourceResolver {

    private final TaskRepository taskRepository;
    private final AuthorizationRequestBuilder requestBuilder;

    public TaskAuthorizationResolver(TaskRepository taskRepository,
                                     AuthorizationRequestBuilder requestBuilder) {
        this.taskRepository = taskRepository;
        this.requestBuilder = requestBuilder;
    }

    @Override
    public boolean supportsResourceType(String resourceType) {
        return "Task".equalsIgnoreCase(resourceType);
    }

    @Override
    public boolean supportsClass(Class<?> targetClass) {
        return Task.class.isAssignableFrom(targetClass) || requestBuilder.supportsDto(targetClass, "TASK");
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode) {
        if (targetDomainObject instanceof Task task) {
            WorkspaceType type = WorkspaceTypeResolver.fromTask(task);
            Map<String, Long> context = new HashMap<>();
            if (task.getOrg() != null) context.put("organizationId", task.getOrg().getId());
            if (task.getProject() != null) context.put("projectId", task.getProject().getId());
            if (task.getProject() != null && task.getProject().getTeam() != null) context.put("teamId", task.getProject().getTeam().getId());
            
            if (task.getCrew() != null) {
                context.put("crewId", task.getCrew().getId());
            } else if (type == WorkspaceType.CREW && task.getProject() != null && task.getProject().getCrew() != null) {
                context.put("crewId", task.getProject().getCrew().getId());
            }

            Set<OwnershipRole> ownership = EnumSet.noneOf(OwnershipRole.class);
            if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) {
                ownership.add(OwnershipRole.ASSIGNEE);
            }
            if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) {
                ownership.add(OwnershipRole.CREATOR);
            }
            if (task.getReviewer() != null && task.getReviewer().getId().equals(user.getId())) {
                ownership.add(OwnershipRole.REVIEWER);
            }

            Map<String, Object> policyContext = new HashMap<>();
            if (task.getAssignee() != null) {
                policyContext.put("targetUserId", task.getAssignee().getId());
            }

            return requestBuilder.build(
                    user,
                    permissionCode,
                    "TASK",
                    task.getId(),
                    type,
                    ScopeType.valueOf(PermissionMetadataRegistry.getRecommendedScope(permissionCode.name())),
                    context,
                    ownership,
                    policyContext
            );
        } else if (requestBuilder.supportsDto(targetDomainObject.getClass(), "TASK")) {
            return requestBuilder.buildFromDto(user, permissionCode, targetDomainObject, "TASK");
        }
        return null;
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode) {
        if (!(targetId instanceof Long taskId)) return null;
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) return null;
        return buildRequest(auth, user, task, permissionCode);
    }
}