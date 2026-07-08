package com.example.taskflow.dto;

import com.example.taskflow.domain.TaskPriority;

public record TaskPriorityBreakdownDTO(TaskPriority priority, long count, String color) {}
