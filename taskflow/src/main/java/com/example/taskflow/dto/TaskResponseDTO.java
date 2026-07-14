package com.example.taskflow.dto;

import java.time.LocalDate;
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
    private String assignee;
    private String creator;
    private String reviewer;
    private TaskStatus currentStatus;
    private TaskPriority priority;
    private LocalDate dueDate;
    private String tags;
    private List<ChecklistItemDTO> checklists;
    private List<TaskSummaryDTO> blocks;
    private List<TaskSummaryDTO> blockedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String coverImageUrl;
    private String rejectionReason;
    @com.fasterxml.jackson.annotation.JsonProperty("isPersonal")
    private boolean isPersonal;
    private boolean archived;
    private Long orgId;
    private String orgName;
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private Long crewId;
    private String crewName;
}
