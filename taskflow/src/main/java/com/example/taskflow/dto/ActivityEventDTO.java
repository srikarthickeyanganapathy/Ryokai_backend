package com.example.taskflow.dto;

import java.time.LocalDateTime;

public record ActivityEventDTO(
        Long id,
        Long taskId,
        String taskTitle,
        String eventType,
        String fromStatus,
        String toStatus,
        String reason,
        UserSummaryDTO actor,
        LocalDateTime occurredAt,
        String relativeTime
) {}
