package com.example.taskflow.dto;

import java.time.LocalDateTime;

import com.example.taskflow.domain.TaskPriority;

public record TaskTemplateDTO(
    Long id,
    String name,
    String defaultTitle,
    String defaultDescription,
    TaskPriority defaultPriority,
    String createdBy,
    LocalDateTime createdAt
) {}
