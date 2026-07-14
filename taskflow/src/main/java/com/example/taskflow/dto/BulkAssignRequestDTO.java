package com.example.taskflow.dto;

import java.time.LocalDate;
import java.util.List;

public class BulkAssignRequestDTO {

    private String title;
    
    private String description;

    /** Optional: if provided, auto-resolve all team members as assignees */
    private Long teamId;

    /** Required if teamId is null — explicit list of usernames to assign to */
    private List<String> assigneeUsernames;

    private LocalDate dueDate;
    private List<String> tags;

    public BulkAssignRequestDTO() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public List<String> getAssigneeUsernames() { return assigneeUsernames; }
    public void setAssigneeUsernames(List<String> assigneeUsernames) { this.assigneeUsernames = assigneeUsernames; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}

