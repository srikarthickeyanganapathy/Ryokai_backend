package com.example.taskflow.dto;

import jakarta.validation.constraints.NotNull;

public class TaskDependencyRequestDTO {

    @NotNull(message = "Blocking task ID cannot be null")
    private Long blocksTaskId;

    public TaskDependencyRequestDTO() {}

    public TaskDependencyRequestDTO(Long blocksTaskId) {
        this.blocksTaskId = blocksTaskId;
    }

    public Long getBlocksTaskId() { return blocksTaskId; }
    public void setBlocksTaskId(Long blocksTaskId) { this.blocksTaskId = blocksTaskId; }
}
