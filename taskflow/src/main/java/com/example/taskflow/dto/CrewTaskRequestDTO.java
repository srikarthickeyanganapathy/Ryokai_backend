package com.example.taskflow.dto;

import java.time.LocalDate;

import com.example.taskflow.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for creating crew tasks via POST /api/crews/{crewId}/tasks.
 * Crew tasks have no assignee field (claim-based model).
 * The crewId comes from the path variable, not the DTO.
 */
public class CrewTaskRequestDTO {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private TaskPriority priority;
    private LocalDate dueDate;
    private String tags;
    private Long projectId;

    public CrewTaskRequestDTO() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
}
