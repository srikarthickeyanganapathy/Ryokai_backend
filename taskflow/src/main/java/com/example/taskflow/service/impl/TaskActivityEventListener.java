package com.example.taskflow.service.impl;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskActivityLog;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.events.task.EvidenceUploadedEvent;
import com.example.taskflow.domain.events.task.TaskStatusChangedEvent;
import com.example.taskflow.repository.TaskActivityLogRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskActivityEventListener {

    private final TaskActivityLogRepository activityLogRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStatusChange(TaskStatusChangedEvent event) {
        try {
            Task task = taskRepository.findById(event.taskId()).orElse(null);
            if (task == null) return;

            User actor = event.actorId() != null ? userRepository.findById(event.actorId()).orElse(null) : null;

            var metadata = objectMapper.createObjectNode();
            var before = metadata.putObject("before");
            if (event.oldStatus() != null) before.put("status", event.oldStatus().name());

            var after = metadata.putObject("after");
            if (event.newStatus() != null) after.put("status", event.newStatus().name());

            if (event.reason() != null) metadata.put("reason", event.reason());

            TaskActivityLog logEntry = TaskActivityLog.builder()
                .task(task)
                .actor(actor)
                .actionType("STATUS_CHANGED")
                .entityType("Task")
                .entityId(task.getId())
                .metadataJson(objectMapper.writeValueAsString(metadata))
                .source(event.source())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .correlationId(event.correlationId())
                .build();

            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to record task status change activity log for task {}", event.taskId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEvidenceUploaded(EvidenceUploadedEvent event) {
        try {
            Task task = taskRepository.findById(event.taskId()).orElse(null);
            if (task == null) return;

            User actor = event.actorId() != null ? userRepository.findById(event.actorId()).orElse(null) : null;

            var metadata = objectMapper.createObjectNode();
            metadata.put("evidenceId", event.evidenceId());
            metadata.put("evidenceType", event.evidenceType());
            if (event.title() != null) metadata.put("title", event.title());

            TaskActivityLog logEntry = TaskActivityLog.builder()
                .task(task)
                .actor(actor)
                .actionType("EVIDENCE_UPLOADED")
                .entityType("TaskEvidence")
                .entityId(event.evidenceId())
                .metadataJson(objectMapper.writeValueAsString(metadata))
                .source(event.source())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .correlationId(event.correlationId())
                .build();

            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to record task evidence activity log for task {}", event.taskId(), e);
        }
    }
}
