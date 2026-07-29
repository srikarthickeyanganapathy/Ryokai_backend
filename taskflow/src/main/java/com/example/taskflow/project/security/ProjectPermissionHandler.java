package com.example.taskflow.project.security;

import java.io.Serializable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.project.dto.ProjectRequestDTO;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.organization.rbac.application.PermissionService;
import com.example.taskflow.security.authorization.LegacyPermissionMapper;
import com.example.taskflow.security.DomainPermissionHandler;
import com.example.taskflow.security.PermissionCode;

@Component
public class ProjectPermissionHandler implements DomainPermissionHandler {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final PermissionService permissionService;

    public ProjectPermissionHandler(ProjectRepository projectRepository,
                                    OrganizationRepository organizationRepository,
                                    TeamRepository teamRepository,
                                    CrewMemberRepository crewMemberRepository,
                                    PermissionService permissionService) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.crewMemberRepository = crewMemberRepository;
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
        if (user == null) return false;

        if (targetDomainObject instanceof Project project) {
            return checkProjectPermission(user, project, permission);
        }

        if (targetDomainObject instanceof ProjectRequestDTO dto) {
            return checkProjectCreateRequest(user, dto, permission);
        }

        return false;
    }

    private boolean checkProjectCreateRequest(User user, ProjectRequestDTO dto, String permission) {
        if ("CREATE".equals(permission) || "PROJECT_CREATE".equals(permission)) {
            // Organization Workspace creation -> RBAC
            if (dto.getTeamId() != null) {
                Team team = teamRepository.findById(dto.getTeamId()).orElse(null);
                if (team != null && team.getOrganization() != null) {
                    if (!isOrganizationActive(team.getOrganization(), user)) return false;
                    return permissionService.isAuthorized(user, PermissionCode.PROJECT_CREATE, team.getOrganization().getId());
                }
            }
            if (dto.getOrganizationId() != null) {
                Organization org = organizationRepository.findById(dto.getOrganizationId()).orElse(null);
                if (org != null) {
                    if (!isOrganizationActive(org, user)) return false;
                    return permissionService.isAuthorized(user, PermissionCode.PROJECT_CREATE, org.getId());
                }
            }

            // Crew Workspace creation -> Crew member check (NO RBAC)
            if (dto.getCrewId() != null) {
                return crewMemberRepository.existsByIdCrewIdAndIdUserId(dto.getCrewId(), user.getId()) || user.isSuperAdmin();
            }

            // Personal Workspace creation -> Allow authenticated user (NO RBAC)
            return true;
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission) {
        if (user == null) return false;

        if (targetId == null) {
            // Null target project id (e.g. general creation check)
            if ("CREATE".equals(permission) || "PROJECT_CREATE".equals(permission)) {
                return true;
            }
            return false;
        }

        Long projectId;
        try {
            projectId = ((Number) targetId).longValue();
        } catch (ClassCastException e) {
            return false;
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return false;
        }
        return checkProjectPermission(user, project, permission);
    }

    private boolean checkProjectPermission(User user, Project project, String permission) {
        if (user == null || project == null) return false;

        // 1. ORGANIZATION WORKSPACE PROJECT -> ENFORCE RBAC & CREATOR/COLLABORATOR ACCESS
        if (project.getOrganization() != null) {
            if (!isOrganizationActive(project.getOrganization(), user)) {
                return false;
            }
            if (user.isSuperAdmin()) return true;

            boolean isCreator = project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId());
            boolean isCollaborator = project.getCollaborators() != null && project.getCollaborators().stream().anyMatch(c -> c.getId().equals(user.getId()));

            PermissionCode code = LegacyPermissionMapper.resolveForDomain("PROJECT", permission);
            if (code != null && permissionService.isAuthorized(user, code, project.getOrganization().getId(), "PROJECT", project.getId())) {
                return true;
            }

            return switch (permission) {
                case "READ", "PROJECT_READ", "VIEW", "PROJECT_VIEW" -> isCreator || isCollaborator;
                case "EDIT", "PROJECT_EDIT" -> isCreator;
                case "DELETE", "PROJECT_DELETE", "PROJECT_MANAGE" -> isCreator;
                default -> false;
            };
        }

        // 2. CREW WORKSPACE PROJECT -> NO RBAC, CREW MEMBERSHIP ONLY
        if (project.getCrew() != null) {
            if (user.isSuperAdmin()) return true;
            boolean isCrewMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(project.getCrew().getId(), user.getId());
            if (isCrewMember) return true;
            if (project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId())) return true;
            return project.getCollaborators() != null && project.getCollaborators().stream().anyMatch(c -> c.getId().equals(user.getId()));
        }

        // 3. PERSONAL WORKSPACE PROJECT -> NO RBAC, CREATOR/COLLABORATOR ONLY
        boolean isCreator = project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId());
        if (isCreator) return true;
        if (project.getCollaborators() != null && project.getCollaborators().stream().anyMatch(c -> c.getId().equals(user.getId()))) return true;

        return false;
    }
}