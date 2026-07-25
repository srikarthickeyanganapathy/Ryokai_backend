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
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;

    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        ApplicationEventPublisher applicationEventPublisher,
                        ObjectMapper objectMapper,
                        TransactionTemplate transactionTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.events.outbox.poll-interval-ms:1000}")
    public void pollAndDispatch() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) return;

        log.debug("Polling outbox: found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            boolean success = false;
            Exception error = null;
            try {
                Class<?> eventClass = Class.forName(event.getEventType());
                Object deserializedEvent = objectMapper.readValue(event.getPayload(), eventClass);
                applicationEventPublisher.publishEvent(deserializedEvent);
                success = true;
            } catch (Exception e) {
                log.error("Error processing outbox event ID {}: {}", event.getId(), e.getMessage(), e);
                error = e;
            }

            final boolean finalSuccess = success;
            final Exception finalError = error;
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    OutboxEvent currentEvent = outboxEventRepository.findById(event.getId()).orElse(event);
                    if (finalSuccess) {
                        currentEvent.setStatus(OutboxStatus.PROCESSED);
                        currentEvent.setProcessedAt(LocalDateTime.now());
                        log.debug("Successfully dispatched outbox event ID {}", currentEvent.getId());
                    } else {
                        currentEvent.setRetryCount(currentEvent.getRetryCount() + 1);
                        String msg = finalError != null ? finalError.getMessage() : "Unknown error";
                        currentEvent.setErrorMessage(msg != null && msg.length() > 950 ? msg.substring(0, 950) : msg);

                        if (currentEvent.getRetryCount() >= 3) {
                            currentEvent.setStatus(OutboxStatus.FAILED);
                            log.error("Outbox event ID {} failed permanently after 3 retries", currentEvent.getId());
                        }
                    }
                    outboxEventRepository.save(currentEvent);
                });
            } catch (Exception ex) {
                log.error("Failed to update status for outbox event ID {}: {}", event.getId(), ex.getMessage(), ex);
            }
        }
    }
}
