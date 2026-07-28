package com.example.taskflow.task.api.response;

import java.time.LocalDateTime;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.project.domain.ProjectActivityLog;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskActivityLog;
import com.example.taskflow.user.dto.UserSummaryDTO;

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
    public static ActivityLogDTO fromTaskLog(com.example.taskflow.task.domain.model.TaskActivityLog log) {
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
    
    public static ActivityLogDTO fromProjectLog(com.example.taskflow.project.domain.ProjectActivityLog log) {
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