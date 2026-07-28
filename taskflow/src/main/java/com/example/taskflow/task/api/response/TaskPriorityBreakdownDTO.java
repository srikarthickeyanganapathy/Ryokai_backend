package com.example.taskflow.task.api.response;

import com.example.taskflow.task.domain.model.TaskPriority;

public record TaskPriorityBreakdownDTO(TaskPriority priority, long count, String color) {}
