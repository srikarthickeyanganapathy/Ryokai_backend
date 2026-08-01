package com.example.taskflow.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusPanelDTO {
    private Long id;
    private String title;
    private String status;
    private int progress;
    private String remainingTime;
    private LocalDate dueDate;
    private String nextChecklistItem;
    private List<String> blockers;
    private List<String> collaborators;
    private String priority;
    private String estimatedCompletion;
}
