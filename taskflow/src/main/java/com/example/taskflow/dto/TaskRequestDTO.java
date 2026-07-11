package com.example.taskflow.dto;

import java.time.LocalDateTime;

import com.example.taskflow.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskRequestDTO {

    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    
    private String assigneeUsername;
    private String creatorUsername; // Optional, usually derived from token
    private TaskPriority priority;
    private LocalDateTime dueDate;
    private String tags;
    @JsonProperty("isPersonal")
    private boolean isPersonal = false;
    private Long projectId;

    public TaskRequestDTO() {}

    // Standard Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAssigneeUsername() { return assigneeUsername; }
    public void setAssigneeUsername(String assigneeUsername) { this.assigneeUsername = assigneeUsername; }

    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isPersonal() { return isPersonal; }
    public void setPersonal(boolean personal) { isPersonal = personal; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
}