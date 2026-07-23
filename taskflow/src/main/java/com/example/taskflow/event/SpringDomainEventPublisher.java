package com.example.taskflow.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Default Spring ApplicationEventPublisher implementation of DomainEventPublisher.
 * Active when `app.events.publisher=spring` or property is omitted.
 */
@Component
@ConditionalOnProperty(name = "app.events.publisher", havingValue = "spring", matchIfMissing = true)
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(Object event) {
        if (event != null) {
            applicationEventPublisher.publishEvent(event);
        }
    }
}
