package com.example.taskflow.organization.rbac.application;

import com.example.taskflow.organization.rbac.dto.ResourceLookupDTO;
import com.example.taskflow.project.infrastructure.persistence.ProjectRepository;
import com.example.taskflow.team.infrastructure.persistence.TeamRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ResourceLookupServiceImpl implements ResourceLookupService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final CrewRepository crewRepository;

    public ResourceLookupServiceImpl(ProjectRepository projectRepository, TeamRepository teamRepository, CrewRepository crewRepository) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.crewRepository = crewRepository;
    }

    @Override
    public List<ResourceLookupDTO> lookupResources(Long organizationId, String resourceType) {
        switch (resourceType.toUpperCase()) {
            case "PROJECT":
                return projectRepository.findByOrganizationId(organizationId).stream()
                    .map(p -> new ResourceLookupDTO(p.getId(), p.getName(), "Project", null, "active"))
                    .collect(Collectors.toList());
            case "TEAM":
                return teamRepository.findByOrganizationId(organizationId).stream()
                    .map(t -> new ResourceLookupDTO(t.getId(), t.getName(), "Team", null, "active"))
                    .collect(Collectors.toList());
            case "CREW":
                return crewRepository.findAll().stream()
                    .map(c -> new ResourceLookupDTO(c.getId(), c.getName(), "Crew", c.getAvatarUrl(), "active"))
                    .collect(Collectors.toList());
            default:
                return Collections.emptyList();
        }
    }
}
