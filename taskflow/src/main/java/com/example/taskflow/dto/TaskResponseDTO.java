package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponseDTO {
    private Long id;
    private String title;
    private String description;
    private String assignedTo;
    private String createdBy;
    private String reviewedBy;
    private TaskStatus currentStatus;
    private TaskPriority priority;
    private LocalDateTime dueDate;
    private String tags;
    private List<ChecklistItemDTO> checklists;
    private List<TaskSummaryDTO> blocks;
    private List<TaskSummaryDTO> blockedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String coverImageUrl;
    @com.fasterxml.jackson.annotation.JsonProperty("isPersonal")
    private boolean isPersonal;
    private boolean archived;
    private Long organizationId;
    private String organizationName;
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
}
