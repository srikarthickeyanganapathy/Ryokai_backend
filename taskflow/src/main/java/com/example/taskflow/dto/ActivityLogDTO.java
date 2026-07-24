package com.example.taskflow.dto;

import java.time.LocalDateTime;

public record ActivityLogDTO(
    Long id,
    Long contextId,
    String contextType,
    UserSummaryDTO actor,
    String actionType,
    String entityType,
    Long entityId,
    String metadataJson,
    String source,
    String ipAddress,
    String correlationId,
    LocalDateTime createdAt
) {
    public static ActivityLogDTO fromTaskLog(com.example.taskflow.domain.TaskActivityLog log) {
        UserSummaryDTO actorDto = null;
        if (log.getActor() != null) {
            actorDto = new UserSummaryDTO(log.getActor().getId(), log.getActor().getUsername());
        }
        
        return new ActivityLogDTO(
            log.getId(),
            log.getTask() != null ? log.getTask().getId() : null,
            "TASK",
            actorDto,
            log.getActionType(),
            log.getEntityType(),
            log.getEntityId(),
            log.getMetadataJson(),
            log.getSource() != null ? log.getSource().name() : null,
            log.getIpAddress(),
            log.getCorrelationId(),
            log.getCreatedAt()
        );
    }
    
    public static ActivityLogDTO fromProjectLog(com.example.taskflow.domain.ProjectActivityLog log) {
        UserSummaryDTO actorDto = null;
        if (log.getActor() != null) {
            actorDto = new UserSummaryDTO(log.getActor().getId(), log.getActor().getUsername());
        }
        
        return new ActivityLogDTO(
            log.getId(),
            log.getProject() != null ? log.getProject().getId() : null,
            "PROJECT",
            actorDto,
            log.getActionType(),
            log.getEntityType(),
            log.getEntityId(),
            log.getMetadataJson(),
            log.getSource() != null ? log.getSource().name() : null,
            log.getIpAddress(),
            log.getCorrelationId(),
            log.getCreatedAt()
        );
    }
}
