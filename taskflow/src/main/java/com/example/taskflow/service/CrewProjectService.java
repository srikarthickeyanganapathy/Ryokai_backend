package com.example.taskflow.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.Crew;
import com.example.taskflow.domain.CrewRole;
import com.example.taskflow.domain.Project;
import com.example.taskflow.domain.User;
import com.example.taskflow.exception.ResourceNotFoundException;
import com.example.taskflow.repository.CrewRepository;
import com.example.taskflow.repository.ProjectRepository;

@Service
public class CrewProjectService {

    private final CrewRepository crewRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    public CrewProjectService(CrewRepository crewRepository,
                              ProjectRepository projectRepository,
                              ProjectService projectService) {
        this.crewRepository = crewRepository;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public List<com.example.taskflow.dto.ProjectResponseDTO> getCrewProjects(Long crewId, User user) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new ResourceNotFoundException("Crew not found"));
        
        boolean isMember = crew.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));
        if (!isMember) {
            throw new IllegalArgumentException("Only crew members can view crew projects");
        }

        List<Project> sharedProjects = projectRepository.findAll().stream()
                .filter(p -> p.getSharedCrews().contains(crew))
                .collect(Collectors.toList());

        return sharedProjects.stream()
                .map(projectService::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public com.example.taskflow.dto.ProjectResponseDTO shareProject(Long crewId, Long projectId, User user) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new ResourceNotFoundException("Crew not found"));

        boolean isMember = crew.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));
        if (!isMember) {
            throw new IllegalArgumentException("You must be a member of the crew to share a project to it");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You can only share projects that you created");
        }

        if (project.getOrganization() != null || project.getTeam() != null) {
            throw new IllegalArgumentException("Only personal projects can be shared to a crew");
        }

        project.getSharedCrews().add(crew);
        projectRepository.save(project);

        return projectService.toResponseDTO(project);
    }

    @Transactional
    public void unshareProject(Long crewId, Long projectId, User user) {
        Crew crew = crewRepository.findById(crewId)
                .orElseThrow(() -> new ResourceNotFoundException("Crew not found"));

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        // Only the project creator or a crew admin can unshare
        boolean isProjectCreator = project.getCreatedBy().getId().equals(user.getId());
        boolean isCrewAdmin = crew.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getRole() == CrewRole.CREATOR);

        if (!isProjectCreator && !isCrewAdmin) {
            throw new IllegalArgumentException("You must be the project creator or a crew admin to unshare a project");
        }

        if (project.getSharedCrews().contains(crew)) {
            project.getSharedCrews().remove(crew);
            projectRepository.save(project);
        }
    }
}
