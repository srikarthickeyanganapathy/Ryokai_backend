package com.example.taskflow.task.api.request;

import jakarta.validation.constraints.NotNull;

public class TaskReassignRequestDTO {

    @NotNull(message = "Assignee ID is required")
    private Long assigneeId;

    public TaskReassignRequestDTO() {}

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
}
