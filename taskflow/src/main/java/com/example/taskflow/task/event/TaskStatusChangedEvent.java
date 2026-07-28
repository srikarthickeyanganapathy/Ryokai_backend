package com.example.taskflow.task.event;

import java.util.Map;

import com.example.taskflow.audit.domain.AuditEventSource;
import com.example.taskflow.task.domain.model.TaskStatus;

public record TaskStatusChangedEvent(
    int eventVersion,
    Long taskId,
    Long actorId,
    TaskStatus oldStatus,
    TaskStatus newStatus,
    String reason,
    Map<String, Object> additionalMetadata,
    AuditEventSource source,
    String ipAddress,
    String userAgent,
    String correlationId
) {
    public TaskStatusChangedEvent(Long taskId, Long actorId, TaskStatus oldStatus, TaskStatus newStatus, String reason) {
        this(1, taskId, actorId, oldStatus, newStatus, reason, Map.of(), AuditEventSource.API, null, null, null);
    }
}
