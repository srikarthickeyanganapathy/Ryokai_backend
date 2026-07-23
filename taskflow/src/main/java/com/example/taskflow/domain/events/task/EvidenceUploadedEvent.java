package com.example.taskflow.domain.events.task;

import java.util.Map;

import com.example.taskflow.domain.AuditEventSource;

public record EvidenceUploadedEvent(
    int eventVersion,
    Long taskId,
    Long evidenceId,
    Long actorId,
    String evidenceType,
    String title,
    Map<String, Object> additionalMetadata,
    AuditEventSource source,
    String ipAddress,
    String userAgent,
    String correlationId
) {
    public EvidenceUploadedEvent(Long taskId, Long evidenceId, Long actorId, String evidenceType, String title) {
        this(1, taskId, evidenceId, actorId, evidenceType, title, Map.of(), AuditEventSource.API, null, null, null);
    }
}
