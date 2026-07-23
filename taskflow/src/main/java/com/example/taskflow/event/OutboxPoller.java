package com.example.taskflow.event;

import com.example.taskflow.domain.OutboxEvent;
import com.example.taskflow.domain.OutboxStatus;
import com.example.taskflow.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Asynchronous Outbox Poller that dispatches pending OutboxEvents to ApplicationEventPublisher.
 * Active when `app.events.publisher=outbox`.
 */
@Component
@ConditionalOnProperty(name = "app.events.publisher", havingValue = "outbox")
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        ApplicationEventPublisher applicationEventPublisher,
                        ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.events.outbox.poll-interval-ms:1000}")
    @Transactional
    public void pollAndDispatch() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) return;

        log.debug("Polling outbox: found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                Class<?> eventClass = Class.forName(event.getEventType());
                Object deserializedEvent = objectMapper.readValue(event.getPayload(), eventClass);

                applicationEventPublisher.publishEvent(deserializedEvent);

                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                log.debug("Successfully dispatched outbox event ID {}", event.getId());
            } catch (Exception e) {
                log.error("Error processing outbox event ID {}: {}", event.getId(), e.getMessage(), e);
                event.setRetryCount(event.getRetryCount() + 1);
                event.setErrorMessage(e.getMessage() != null && e.getMessage().length() > 950 
                        ? e.getMessage().substring(0, 950) : e.getMessage());

                if (event.getRetryCount() >= 3) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event ID {} failed permanently after 3 retries", event.getId());
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
