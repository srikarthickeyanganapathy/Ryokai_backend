package com.example.taskflow.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.dto.ProjectRequestDTO;
import com.example.taskflow.dto.ProjectResponseDTO;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.domain.CrewMember;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.TeamRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final PermissionService permissionService;
    private final com.example.taskflow.repository.TeamObserverRepository teamObserverRepository;
    private final com.example.taskflow.repository.CrewRepository crewRepository;
    private final com.example.taskflow.repository.UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          OrganizationRepository organizationRepository,
                          TeamRepository teamRepository,
                          OrganizationMembershipRepository membershipRepository,
                          TeamMemberRepository teamMemberRepository,
                          CrewMemberRepository crewMemberRepository,
                          PermissionService permissionService,
                          com.example.taskflow.repository.TeamObserverRepository teamObserverRepository,
                          com.example.taskflow.repository.CrewRepository crewRepository,
                          com.example.taskflow.repository.UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.permissionService = permissionService;
        this.teamObserverRepository = teamObserverRepository;
        this.crewRepository = crewRepository;
        this.userRepository = userRepository;
    }

    private boolean hasOrgPermission(User user, Organization org, String permission) {
        if (user.isSuperAdmin()) return true;
        return membershipRepository.findByUserAndOrganization(user, org)
                .map(m -> m.getOrgRole() != null && m.getOrgRole().getPermissions().stream().anyMatch(p -> p.getName().equals(permission)))
                .orElse(false);
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
            boolean hasProjectManage = hasOrgPermission(currentUser, org, "PROJECT_MANAGE");
            boolean hasSuperAdminOverride = permissionService.hasPermission(currentUser, "SUPER_ADMIN_OVERRIDE_CHECK");

            List<Project> orgProjects = projectRepository.findByOrganizationId(org.getId());
            
            for (Project p : orgProjects) {
                if (p.getTeam() == null) {
                    // Organization-scoped projects (no team) are visible to all org members
                    result.add(p);
                } else {
                    // Team-scoped projects: check visibility
                    boolean isTeamMember = teamMemberRepository.existsByIdTeamIdAndIdUserId(p.getTeam().getId(), currentUser.getId());
                    boolean isTeamObserver = teamObserverRepository.existsByIdTeamIdAndIdUserId(p.getTeam().getId(), currentUser.getId());
                    
                    if (isTeamMember || isTeamObserver || hasProjectManage || hasSuperAdminOverride) {
                        result.add(p);
                    }
                }
            }
        }
        return result.stream().map(this::toResponseDTO).collect(Collectors.toList());
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
            if (!hasOrgPermission(currentUser, team.getOrganization(), "PROJECT_CREATE")) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create organizational projects.");
            }
            project.setTeam(team);
            // Automatically set organization to team's organization
            project.setOrganization(team.getOrganization());
        } else if (dto.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            if (!hasOrgPermission(currentUser, org, "PROJECT_CREATE")) {
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
        
        boolean changingOrgOrTeam = dto.getTeamId() != null || dto.getOrganizationId() != null;
        if (changingOrgOrTeam && project.getOrganization() != null) {
            if (!hasOrgPermission(currentUser, project.getOrganization(), "PROJECT_MANAGE")) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to move this project from its current organization.");
            }
        }

        if (dto.getTeamId() != null) {
            Team team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));
            if (!hasOrgPermission(currentUser, team.getOrganization(), "PROJECT_CREATE")) {
                throw new org.springframework.security.access.AccessDeniedException("You do not have permission to create or move projects to this team.");
            }
            project.setTeam(team);
            project.setOrganization(team.getOrganization());
        } else if (dto.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            if (!hasOrgPermission(currentUser, org, "PROJECT_CREATE")) {
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

        if (project.getCrew() == null) {
            throw new IllegalStateException("Project is not currently shared with a crew.");
        }

        // Decouple tasks from the project without deleting them or their crew scope.
        taskRepository.detachProjectFromTasks(projectId);

        project.setCrew(null);
        project.setCollaborators(new java.util.HashSet<>());

        return toResponseDTO(projectRepository.save(project));
    }



    public ProjectResponseDTO toResponseDTO(Project p) {
        long total = taskRepository.countByProjectId(p.getId());
        long completed = taskRepository.countByProjectIdAndCurrentStatusIn(
                p.getId(), java.util.List.of(TaskStatus.APPROVED, TaskStatus.COMPLETED));
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
            dto.setCollaboratorIds(p.getCollaborators().stream().map(User::getId).collect(Collectors.toList()));
        } else {
            dto.setCollaboratorIds(new java.util.ArrayList<>());
        }
        
        if (p.getSharedCrews() != null) {
            dto.setSharedCrewIds(p.getSharedCrews().stream().map(Crew::getId).collect(java.util.stream.Collectors.toList()));
        } else {
            dto.setSharedCrewIds(new java.util.ArrayList<>());
        }
        
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}
