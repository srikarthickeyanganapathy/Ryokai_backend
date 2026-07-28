package com.example.taskflow.task.api.response;

import com.example.taskflow.task.domain.model.TaskStatus;
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
