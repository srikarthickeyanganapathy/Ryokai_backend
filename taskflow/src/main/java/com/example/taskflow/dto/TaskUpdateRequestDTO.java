package com.example.taskflow.dto;

import java.time.LocalDate;

import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskStatus;

import jakarta.validation.constraints.Size;

public class TaskUpdateRequestDTO {

    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;
    
    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;
    
    private TaskPriority priority;
    
    private LocalDate dueDate;
    
    @Size(max = 200, message = "Tags cannot exceed 200 characters")
    private String tags;

    private TaskStatus status;

    public TaskUpdateRequestDTO() {}

    // Standard Getters and Setters
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

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
}
