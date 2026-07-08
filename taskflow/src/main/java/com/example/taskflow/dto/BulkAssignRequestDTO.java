package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public class BulkAssignRequestDTO {

    private Long templateId;

    private String title;
    
    private String description;

    @NotEmpty(message = "At least one assignee is required")
    private List<String> assigneeUsernames;

    private LocalDateTime dueDate;
    private List<String> tags; // Changed to List<String> from Set<String> to match frontend if needed, wait backend expects List in controller but DTO has Set. Controller uses request.getTags(), let's leave it as List since frontend sends array

    public BulkAssignRequestDTO() {}

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getAssigneeUsernames() { return assigneeUsernames; }
    public void setAssigneeUsernames(List<String> assigneeUsernames) { this.assigneeUsernames = assigneeUsernames; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
