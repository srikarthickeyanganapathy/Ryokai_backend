package com.example.taskflow.task.api.request;

import jakarta.validation.constraints.NotNull;
import com.example.taskflow.task.domain.model.Task;

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