package com.example.taskflow.event;

/**
 * Domain Event Publisher Abstraction.
 * Decouples domain services from specific transport/event-bus implementations
 * (Spring ApplicationEventPublisher, Kafka, RabbitMQ, Transactional Outbox Pattern).
 */
public interface DomainEventPublisher {
    void publish(Object event);
}
