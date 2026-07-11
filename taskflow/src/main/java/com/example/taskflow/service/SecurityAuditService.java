package com.example.taskflow.service;

import com.example.taskflow.domain.SecurityAuditEvent;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.SecurityAuditEventRepository;
import com.example.taskflow.repository.UserRepository;
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
public class SecurityAuditService {

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // TODO: GDPR retention policy (purge records older than 365 days)

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, Long actorUserId, String actorUsernameSnapshot,
                       String ipAddress, String deviceInfo, Object metadata, boolean success) {
        try {
            User actor = null;
            if (actorUserId != null) {
                actor = userRepository.findById(actorUserId).orElse(null);
            }

            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .actorUsernameSnapshot(actorUsernameSnapshot != null ? actorUsernameSnapshot : (actor != null ? actor.getUsername() : null))
                    .ipAddress(ipAddress)
                    .deviceInfo(deviceInfo)
                    .metadataJson(toJson(metadata))
                    .success(success)
                    .build();
            
            securityAuditEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to record security audit event: type={}, actorId={}", eventType, actorUserId, e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize security audit metadata", e);
            return "{\"error\": \"serialization_failed\"}";
        }
    }
}
