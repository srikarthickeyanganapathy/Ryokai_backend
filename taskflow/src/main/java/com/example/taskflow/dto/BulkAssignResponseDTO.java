package com.example.taskflow.dto;

import java.util.List;
import java.util.Map;

public class BulkAssignResponseDTO {
    private List<TaskResponseDTO> successfulTasks;
    private Map<String, String> failedAssignees;

    public BulkAssignResponseDTO(List<TaskResponseDTO> successfulTasks, Map<String, String> failedAssignees) {
        this.successfulTasks = successfulTasks;
        this.failedAssignees = failedAssignees;
    }

    public List<TaskResponseDTO> getSuccessfulTasks() {
        return successfulTasks;
    }

    public void setSuccessfulTasks(List<TaskResponseDTO> successfulTasks) {
        this.successfulTasks = successfulTasks;
    }

    public Map<String, String> getFailedAssignees() {
        return failedAssignees;
    }

    public void setFailedAssignees(Map<String, String> failedAssignees) {
        this.failedAssignees = failedAssignees;
    }
}
