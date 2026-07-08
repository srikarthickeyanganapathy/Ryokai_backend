package com.example.taskflow.dto;

import com.example.taskflow.domain.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummaryDTO {
    private Long id;
    private String title;
    private TaskStatus currentStatus;
}
