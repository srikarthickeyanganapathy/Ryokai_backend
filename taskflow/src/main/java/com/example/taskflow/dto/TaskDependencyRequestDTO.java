package com.example.taskflow.dto;

import jakarta.validation.constraints.NotNull;

public class TaskDependencyRequestDTO {

    @NotNull(message = "Depends-on task ID cannot be null")
    private Long dependsOnId;

    public TaskDependencyRequestDTO() {}

    public TaskDependencyRequestDTO(Long dependsOnId) {
        this.dependsOnId = dependsOnId;
    }

    public Long getDependsOnId() { return dependsOnId; }
    public void setDependsOnId(Long dependsOnId) { this.dependsOnId = dependsOnId; }
}
