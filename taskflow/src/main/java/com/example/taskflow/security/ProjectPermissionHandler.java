package com.example.taskflow.security;

import java.io.Serializable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.service.PermissionService;

@Component
public class ProjectPermissionHandler implements DomainPermissionHandler {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PermissionService permissionService;

    public ProjectPermissionHandler(ProjectRepository projectRepository,
                                    OrganizationRepository organizationRepository,
                                    OrganizationMembershipRepository membershipRepository,
                                    TeamMemberRepository teamMemberRepository,
                                    PermissionService permissionService) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.permissionService = permissionService;
    }

    @Override
    public String getTargetType() {
        return "Project";
    }

    private boolean isOrganizationActive(Organization org, User user) {
        if (org == null || org.getId() == null) return true;
        if (user != null && user.isSuperAdmin()) return true;
        Organization freshOrg = organizationRepository.findById(org.getId()).orElse(null);
        return freshOrg != null && freshOrg.getStatus() == Organization.OrgStatus.ACTIVE;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Object targetDomainObject, String permission) {
        if (targetDomainObject instanceof Project) {
            Project project = (Project) targetDomainObject;
            return checkProjectPermission(user, project, permission);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission) {
        Project project = targetId == null ? null : projectRepository.findById(((Number) targetId).longValue()).orElse(null);
        if ("CREATE".equals(permission)) {
            return true;
        }
        if (project == null) {
            return false;
        }
        return checkProjectPermission(user, project, permission);
    }

    private boolean checkProjectPermission(User user, Project project, String permission) {
        if (!isOrganizationActive(project.getOrganization(), user)) {
            return false;
        }
        
        return switch (permission) {
            case "CREATE" -> true;
            case "READ" -> 
                (project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId()))
                || (project.getOrganization() != null
                    && membershipRepository.existsByUserAndOrganization(user, project.getOrganization()))
                || (project.getTeam() != null
                    && teamMemberRepository.existsByIdTeamIdAndIdUserId(project.getTeam().getId(), user.getId()))
                || projectRepository.isProjectSharedWithUser(project.getId(), user.getId())
                || permissionService.hasPermission(user, "SUPER_ADMIN_OVERRIDE_CHECK");
            case "EDIT", "DELETE" -> 
                (project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId()))
                || (project.getCollaborators() != null && project.getCollaborators().stream().anyMatch(c -> c.getId().equals(user.getId())))
                || (project.getOrganization() != null 
                    && membershipRepository.existsByUserAndOrganization(user, project.getOrganization())
                    && permissionService.hasPermission(user, "PROJECT_MANAGE"));
            default -> false;
        };
    }
}
