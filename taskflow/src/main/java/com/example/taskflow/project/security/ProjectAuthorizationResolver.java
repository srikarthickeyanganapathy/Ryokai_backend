package com.example.taskflow.project.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.WorkspaceType;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationRequestBuilder;
import com.example.taskflow.security.authorization.OwnershipRole;
import com.example.taskflow.security.authorization.WorkspaceTypeResolver;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.user.domain.User;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ProjectAuthorizationResolver implements AuthorizationResourceResolver {

    private final ProjectRepository projectRepository;
    private final AuthorizationRequestBuilder requestBuilder;

    public ProjectAuthorizationResolver(ProjectRepository projectRepository,
                                        AuthorizationRequestBuilder requestBuilder) {
        this.projectRepository = projectRepository;
        this.requestBuilder = requestBuilder;
    }

    @Override
    public String getTargetType() {
        return "Project";
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode) {
        if (!(targetDomainObject instanceof Project project)) return null;

        WorkspaceType type = WorkspaceTypeResolver.fromDomainObject(project);
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", project.getOrganization() != null ? project.getOrganization().getId() : null);
        context.put("projectId", project.getId());
        context.put("crewId", project.getCrew() != null ? project.getCrew().getId() : null);

        Set<OwnershipRole> ownership = EnumSet.noneOf(OwnershipRole.class);
        if (project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId())) {
            ownership.add(OwnershipRole.CREATOR);
            ownership.add(OwnershipRole.PROJECT_OWNER);
        }

        return requestBuilder.build(
                user,
                permissionCode,
                "PROJECT",
                project.getId(),
                type,
                ScopeType.TEAM,
                context,
                ownership
        );
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode) {
        if (!(targetId instanceof Long projectId)) return null;
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return null;
        return buildRequest(auth, user, project, permissionCode);
    }
}
