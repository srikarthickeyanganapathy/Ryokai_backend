package com.example.taskflow.event;

import com.example.taskflow.domain.OutboxEvent;
import com.example.taskflow.domain.OutboxStatus;
import com.example.taskflow.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Outbox implementation of DomainEventPublisher.
 * Activated when `app.events.publisher=outbox` in application.yml.
 * Atomically writes domain events to `outbox_events` table within the same DB transaction.
 */
@Component
@ConditionalOnProperty(name = "app.events.publisher", havingValue = "outbox")
@Slf4j
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxDomainEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Object event) {
        if (event == null) return;

        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setEventType(event.getClass().getName());
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setStatus(OutboxStatus.PENDING);

            outboxEventRepository.save(outboxEvent);
            log.debug("Persisted outbox event: type={}, payloadLength={}", outboxEvent.getEventType(), outboxEvent.getPayload().length());
        } catch (Exception e) {
            log.error("Failed to serialize and save outbox event of type {}: {}", event.getClass().getName(), e.getMessage(), e);
            throw new RuntimeException("Outbox persistence failed", e);
        }
    }
}
