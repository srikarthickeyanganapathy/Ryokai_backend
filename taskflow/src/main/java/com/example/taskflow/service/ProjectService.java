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

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          OrganizationRepository organizationRepository,
                          TeamRepository teamRepository,
                          OrganizationMembershipRepository membershipRepository,
                          TeamMemberRepository teamMemberRepository,
                          CrewMemberRepository crewMemberRepository,
                          PermissionService permissionService,
                          com.example.taskflow.repository.TeamObserverRepository teamObserverRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.permissionService = permissionService;
        this.teamObserverRepository = teamObserverRepository;
    }

    private boolean hasOrgPermission(User user, Organization org, String permission) {
        if (user.getRoles() != null && user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_SUPER_ADMIN") || r.getName().equals("SUPER_ADMIN"))) return true;
        return membershipRepository.findByUserAndOrganization(user, org)
                .map(m -> m.getOrgRole() != null && m.getOrgRole().getPermissions().stream().anyMatch(p -> p.getName().equals(permission)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getAllProjects(User currentUser) {
        List<Project> result = new java.util.ArrayList<>();
        
        // 1. Add all personal projects
        result.addAll(projectRepository.findByCreatedById(currentUser.getId()).stream()
                .filter(p -> p.getOrganization() == null)
                .collect(Collectors.toList()));
                
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
