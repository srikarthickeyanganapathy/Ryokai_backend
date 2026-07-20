package com.example.taskflow.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private String color;
    private Long organizationId;
    private String organizationName;
    private Long teamId;
    private String teamName;
    private Long crewId;
    private String crewName;
    private List<Long> collaboratorIds;
    private String createdBy;
    private ProjectStatus status;
    private LocalDate dueDate;
    private long tasksTotal;
    private long tasksCompleted;
    private int progress;
    private List<Long> sharedCrewIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
