package com.example.taskflow.dashboard.dto;

import java.time.LocalDateTime;
import com.example.taskflow.user.dto.UserSummaryDTO;

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