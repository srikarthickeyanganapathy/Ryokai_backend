package com.example.taskflow.project.application;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.crew.domain.Crew;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.project.dto.ProjectRequestDTO;
import com.example.taskflow.project.dto.ProjectResponseDTO;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamMemberRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final AuthorizationEngine authorizationEngine;
    private final com.example.taskflow.team.infrastructure.persistence.TeamObserverRepository teamObserverRepository;
    private final com.example.taskflow.crew.infrastructure.persistence.CrewRepository crewRepository;
    private final com.example.taskflow.user.infrastructure.persistence.UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          OrganizationRepository organizationRepository,
                          TeamRepository teamRepository,
                          OrganizationMembershipRepository membershipRepository,
                          TeamMemberRepository teamMemberRepository,
                          CrewMemberRepository crewMemberRepository,
                          AuthorizationEngine authorizationEngine,
                          com.example.taskflow.team.infrastructure.persistence.TeamObserverRepository teamObserverRepository,
                          com.example.taskflow.crew.infrastructure.persistence.CrewRepository crewRepository,
                          com.example.taskflow.user.infrastructure.persistence.UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.authorizationEngine = authorizationEngine;
        this.teamObserverRepository = teamObserverRepository;
        this.crewRepository = crewRepository;
        this.userRepository = userRepository;
    }

    private boolean hasOrgPermission(User user, Organization org, PermissionCode permissionCode) {
        if (user == null || org == null || org.getId() == null) return false;
        if (user.isSuperAdmin()) return true;
        return authorizationEngine.authorize(com.example.taskflow.security.authorization.AuthorizationRequest.builder(user, permissionCode).context(java.util.Map.of("organizationId", org.getId())).requiredScope(com.example.taskflow.security.ScopeType.ORGANIZATION).build()).isGranted();
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getAllProjects(User currentUser) {
        java.util.Set<Project> result = new java.util.HashSet<>();
        
        // 1. Add all personal projects (including crew projects created by user)
        result.addAll(projectRepository.findByCreatedById(currentUser.getId()).stream()
                .filter(p -> p.getOrganization() == null)
                .collect(Collectors.toList()));
                
        // 1.5 Add projects where user is an explicit collaborator
        result.addAll(projectRepository.findByCollaboratorsId(currentUser.getId()));
                
        // 2. Add organizational projects
        var memberships = membershipRepository.findByUserId(currentUser.getId());
        if (!memberships.isEmpty()) {
            Organization org = memberships.get(0).getOrganization();
            List<Project> orgProjects = projectRepository.findByOrganizationId(org.getId());
            for (Project p : orgProjects) {
                if (canViewProject(currentUser, p)) {
                    result.add(p);
                }
            }
        }
        return result.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getAllProjects(User currentUser, String scope, Long orgId, Long crewId) {
        if (scope == null && orgId == null && crewId == null) {
            return getAllProjects(currentUser);
        }
        if ("PERSONAL".equalsIgnoreCase(scope)) {
            java.util.Set<Project> result = new java.util.HashSet<>();
            result.addAll(projectRepository.findByCreatedById(currentUser.getId()).stream()
                    .filter(p -> p.getOrganization() == null && p.getSharedCrews().isEmpty())
                    .collect(Collectors.toList()));
            result.addAll(projectRepository.findByCollaboratorsId(currentUser.getId()).stream()
                    .filter(p -> p.getOrganization() == null && p.getSharedCrews().isEmpty())
                    .collect(Collectors.toList()));
            return result.stream().map(this::toResponseDTO).collect(Collectors.toList());
        } else if ("ORG".equalsIgnoreCase(scope) || "ORGANIZATION".equalsIgnoreCase(scope) || orgId != null) {
            Long targetOrgId = orgId;
            if (targetOrgId == null) {
                var memberships = membershipRepository.findByUserId(currentUser.getId());
                if (memberships.isEmpty()) return java.util.Collections.emptyList();
                targetOrgId = memberships.get(0).getOrganization().getId();
            }
            if (!membershipRepository.existsByUserIdAndOrganizationId(currentUser.getId(), targetOrgId) && !currentUser.isSuperAdmin()) {
                throw new org.springframework.security.access.AccessDeniedException("User is not a member of organization " + targetOrgId);
            }
            return projectRepository.findByOrganizationId(targetOrgId).stream()
                    .filter(p -> canViewProject(currentUser, p))
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());
        } else if ("CREWS".equalsIgnoreCase(scope) || "CREW".equalsIgnoreCase(scope) || crewId != null) {
            if (crewId == null) {
                throw new IllegalArgumentException("Crew ID is required when scope is CREWS");
            }
            if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, currentUser.getId()) && !currentUser.isSuperAdmin()) {
                throw new org.springframework.security.access.AccessDeniedException("User is not a member of crew " + crewId);
            }
            return projectRepository.findProjectsForCrew(crewId).stream()
                    .filter(p -> canViewProject(currentUser, p))
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());
        }
        return getAllProjects(currentUser);
    }

    private boolean canViewProject(User currentUser, Project p) {
        if (currentUser.isSuperAdmin()) return true;
        if (p.getCreatedBy() != null && p.getCreatedBy().getId().equals(currentUser.getId())) return true;
        if (p.getCollaborators() != null && p.getCollaborators().stream().anyMatch(c -> c.getId().equals(currentUser.getId()))) return true;
        if (p.getOrganization() != null) {
            boolean hasProjectManage = hasOrgPermission(currentUser, p.getOrganization(), PermissionCode.PROJECT_UPDATE);
            if (hasProjectManage) return true;
            
            com.example.taskflow.security.authorization.AuthorizationRequest viewReq = com.example.taskflow.security.authorization.AuthorizationRequest.builder(currentUser, PermissionCode.PROJECT_VIEW)
                    .resourceType("PROJECT")
                    .resourceId(p.getId())
                    .requiredScope(com.example.taskflow.security.ScopeType.PROJECT)
                    .context(java.util.Map.of("organizationId", p.getOrganization().getId(), "projectId", p.getId()))
                    .build();
            return authorizationEngine.authorize(viewReq).isGranted();
        }
        if (p.getSharedCrews() != null && !p.getSharedCrews().isEmpty()) {
            return p.getSharedCrews().stream().anyMatch(crew -> crewMemberRepository.existsByIdCrewIdAndIdUserId(crew.getId(), currentUser.getId()));
        }
        return false;
    }

    @Transactional(readOnly = true)
    public ProjectResponseDTO getProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return toResponseDTO(project);
    }

    @Transactional
    public ProjectResponseDTO createProject(ProjectRequestDTO dto, User currentUser) {
        Project project = new Project();
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setDueDate(dto.getDueDate());
        project.setCreatedBy(currentUser);
        project.setStatus(Project.ProjectStatus.ACTIVE);

        if (dto.getTeamId() != null) {
            Team team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));
            if (!hasOrgPermission(currentUser, team.getOrganization(), PermissionCode.PROJECT_CREATE)) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create organizational projects.");
            }
            project.setTeam(team);
            // Automatically set organization to team's organization
            project.setOrganization(team.getOrganization());
        } else if (dto.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            if (!hasOrgPermission(currentUser, org, PermissionCode.PROJECT_CREATE)) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create organizational projects.");
            }
            project.setOrganization(org);
        } else if (dto.getCrewId() != null) {
            Crew crew = crewRepository.findById(dto.getCrewId())
                    .orElseThrow(() -> new RuntimeException("Crew not found"));
            project.setCrew(crew);
            
            if (dto.getCollaboratorIds() != null) {
                java.util.Set<User> collaborators = new java.util.HashSet<>();
                for (Long cid : dto.getCollaboratorIds()) {
                    User u = userRepository.findById(cid).orElseThrow(() -> new RuntimeException("Collaborator not found"));
                    collaborators.add(u);
                }
                project.setCollaborators(collaborators);
            }
        }

        project = projectRepository.save(project);
        return toResponseDTO(project);
    }

    @Transactional
    public ProjectResponseDTO updateProject(Long projectId, ProjectRequestDTO dto, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (dto.getName() != null) project.setName(dto.getName());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        if (dto.getDueDate() != null) project.setDueDate(dto.getDueDate());
        if (dto.getStatus() != null) project.setStatus(dto.getStatus());
        
        boolean changingOrgOrTeam = dto.getTeamId() != null || dto.getOrganizationId() != null;
        if (changingOrgOrTeam && project.getOrganization() != null) {
            if (!hasOrgPermission(currentUser, project.getOrganization(), PermissionCode.PROJECT_UPDATE)) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to move this project from its current organization.");
            }
        }

        if (dto.getTeamId() != null) {
            Team team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));
            if (!hasOrgPermission(currentUser, team.getOrganization(), PermissionCode.PROJECT_CREATE)) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create or move projects to this team.");
            }
            project.setTeam(team);
            project.setOrganization(team.getOrganization());
        } else if (dto.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            if (!hasOrgPermission(currentUser, org, PermissionCode.PROJECT_CREATE)) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create or move projects to this organization.");
            }
            project.setOrganization(org);
        } else if (dto.getCrewId() != null) {
            Crew crew = crewRepository.findById(dto.getCrewId())
                    .orElseThrow(() -> new RuntimeException("Crew not found"));
            project.setCrew(crew);
            
            if (dto.getCollaboratorIds() != null) {
                java.util.Set<User> collaborators = new java.util.HashSet<>();
                for (Long cid : dto.getCollaboratorIds()) {
                    User u = userRepository.findById(cid).orElseThrow(() -> new RuntimeException("Collaborator not found"));
                    collaborators.add(u);
                }
                project.setCollaborators(collaborators);
            }
        }

        project = projectRepository.save(project);
        return toResponseDTO(project);
    }

    @Transactional
    public void deleteProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        taskRepository.detachProjectFromTasks(projectId);
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectResponseDTO shareProjectToCrew(Long projectId, Long crewId, java.util.List<Long> collaboratorIds, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the project creator can share it to a crew.");
        }

        // INVARIANT: Enterprise (org-owned) projects are a sealed vault and can
        // never be shared to a Crew. This must be checked BEFORE any mutation.
        if (project.getOrganization() != null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Enterprise projects cannot be shared with Crews.");
        }

        if (project.getCrew() != null) {
            throw new IllegalStateException("Project is already shared with a crew. Unshare it first to share with another crew.");
        }

        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new RuntimeException("Crew not found"));

        project.setCrew(crew);

        if (collaboratorIds != null) {
            java.util.Set<User> collaborators = new java.util.HashSet<>();
            for (Long cid : collaboratorIds) {
                User u = userRepository.findById(cid).orElseThrow(() -> new RuntimeException("Collaborator not found"));
                collaborators.add(u);
            }
            project.setCollaborators(collaborators);
        }

        return toResponseDTO(projectRepository.save(project));
    }

    /**
     * Revokes crew access to a shared project. Crew-created tasks under the
     * project retain their crew_id (still crew-scoped) but are decoupled from
     * the project (project_id -> null) rather than deleted, per the
     * orphaned-task-lifecycle invariant.
     */
    @Transactional
    public ProjectResponseDTO unshareProjectFromCrew(Long projectId, User currentUser) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the project creator can unshare it from a crew.");
        }

        if (project.getCrew() == null && (project.getSharedCrews() == null || project.getSharedCrews().isEmpty())) {
            throw new IllegalStateException("Project is not currently shared with a crew.");
        }

        // Decouple tasks from the project without deleting them or their crew scope.
        taskRepository.detachProjectFromTasks(projectId);

        project.setCrew(null);
        if (project.getSharedCrews() != null) {
            project.getSharedCrews().clear();
        }
        project.setCollaborators(new java.util.HashSet<>());

        return toResponseDTO(projectRepository.save(project));
    }



    public ProjectResponseDTO toResponseDTO(Project p) {
        long total = p.getTasksTotal() != null ? p.getTasksTotal() : 0L;
        long completed = p.getTasksCompleted() != null ? p.getTasksCompleted() : 0L;
        int progress = total > 0 ? (int) Math.round((completed * 100.0) / total) : 0;

        ProjectResponseDTO dto = new ProjectResponseDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setOrganizationId(p.getOrganization() != null ? p.getOrganization().getId() : null);
        dto.setOrganizationName(p.getOrganization() != null ? p.getOrganization().getName() : null);
        dto.setTeamId(p.getTeam() != null ? p.getTeam().getId() : null);
        dto.setTeamName(p.getTeam() != null ? p.getTeam().getName() : null);
        dto.setCreatedBy(p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : null);
        dto.setStatus(p.getStatus());
        dto.setDueDate(p.getDueDate());
        dto.setTasksTotal(total);
        dto.setTasksCompleted(completed);
        dto.setProgress(progress);
        
        dto.setCrewId(p.getCrew() != null ? p.getCrew().getId() : null);
        dto.setCrewName(p.getCrew() != null ? p.getCrew().getName() : null);
        if (p.getCollaborators() != null) {
            dto.setCollaboratorIds(p.getCollaborators().stream().map(u -> u.getId()).collect(Collectors.toList()));
        } else {
            dto.setCollaboratorIds(new java.util.ArrayList<>());
        }
        
        if (p.getSharedCrews() != null) {
            dto.setSharedCrewIds(p.getSharedCrews().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toList()));
        } else {
            dto.setSharedCrewIds(new java.util.ArrayList<>());
        }
        
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}