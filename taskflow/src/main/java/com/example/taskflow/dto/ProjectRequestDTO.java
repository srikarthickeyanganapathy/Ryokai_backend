package com.example.taskflow.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectRequestDTO {

    @NotBlank(message = "Project name is required")
    @Size(max = 200)
    private String name;

    private String description;
    private String color;
    private Long organizationId;
    private Long teamId;
    private Long crewId;
    private List<Long> collaboratorIds;
    private LocalDate dueDate;

    public ProjectRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getCrewId() { return crewId; }
    public void setCrewId(Long crewId) { this.crewId = crewId; }

    public List<Long> getCollaboratorIds() { return collaboratorIds; }
    public void setCollaboratorIds(List<Long> collaboratorIds) { this.collaboratorIds = collaboratorIds; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
