package com.example.taskflow.dto;

import java.time.LocalDateTime;

import com.example.taskflow.domain.Project.ProjectStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long organizationId;
    private String organizationName;
    private Long teamId;
    private String teamName;
    private String createdBy;
    private ProjectStatus status;
    private LocalDateTime dueDate;
    private long tasksTotal;
    private long tasksCompleted;
    private int progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
