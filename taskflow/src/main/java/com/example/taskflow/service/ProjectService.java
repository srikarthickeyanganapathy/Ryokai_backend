package com.example.taskflow.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ProjectRequestDTO;
import com.example.taskflow.dto.ProjectResponseDTO;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TeamRepository;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository;

    public ProjectService(ProjectRepository projectRepository,
                          TaskRepository taskRepository,
                          OrganizationRepository organizationRepository,
                          TeamRepository teamRepository,
                          com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getAllProjects(User currentUser) {
        List<Project> result = new java.util.ArrayList<>();
        result.addAll(projectRepository.findByCreatedById(currentUser.getId()).stream()
                .filter(p -> p.getOrganization() == null)
                .collect(Collectors.toList()));
        var memberships = membershipRepository.findByUserId(currentUser.getId());
        if (!memberships.isEmpty()) {
            Long orgId = memberships.get(0).getOrganization().getId();
            result.addAll(projectRepository.findByOrganizationId(orgId));
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

        if (dto.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            project.setOrganization(org);
        }
        if (dto.getTeamId() != null) {
            Team team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));
            project.setTeam(team);
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
        if (dto.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(dto.getOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Organization not found"));
            project.setOrganization(org);
        }
        if (dto.getTeamId() != null) {
            Team team = teamRepository.findById(dto.getTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));
            project.setTeam(team);
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

    private ProjectResponseDTO toResponseDTO(Project p) {
        long total = taskRepository.countByProjectId(p.getId());
        long completed = taskRepository.countByProjectIdAndCurrentStatusIn(
                p.getId(), java.util.List.of(com.example.taskflow.domain.TaskStatus.APPROVED, com.example.taskflow.domain.TaskStatus.COMPLETED));
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
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}
