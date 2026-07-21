package com.example.taskflow.service;

import com.example.taskflow.domain.AuditEvent;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSync(String eventType, User actor, String entityType, Long entityId,
                           Object oldValue, Object newValue, String reason) {
        try {
            saveEvent(eventType, actor, entityType, entityId, oldValue, newValue, reason);
        } catch (Exception e) {
            log.error("Failed to synchronously record audit event: type={}, entityType={}, entityId={}",
                    eventType, entityType, entityId, e);
            throw new RuntimeException("Audit recording failed", e);
        }
    }

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, User actor, String entityType, Long entityId,
                       Object oldValue, Object newValue, String reason) {
        try {
            saveEvent(eventType, actor, entityType, entityId, oldValue, newValue, reason);
        } catch (Exception e) {
            log.error("Failed to asynchronously record audit event: type={}, entityType={}, entityId={}",
                    eventType, entityType, entityId, e);
        }
    }

    private void saveEvent(String eventType, User actor, String entityType, Long entityId,
                           Object oldValue, Object newValue, String reason) {
        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .actor(actor)
                .actorUsernameSnapshot(actor != null ? actor.getUsername() : null)
                .entityType(entityType)
                .entityId(entityId)
                .oldValueJson(toJson(oldValue))
                .newValueJson(toJson(newValue))
                .reason(reason)
                .build();
        auditEventRepository.save(event);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit value: {}", obj.getClass().getSimpleName(), e);
            return "{\"error\": \"serialization_failed\"}";
        }
    }
}
