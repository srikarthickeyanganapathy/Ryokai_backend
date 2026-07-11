package com.example.taskflow.service;

import com.example.taskflow.notification.NotificationCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

@Component
public class RealtimeDispatcher {

    private final RealtimeBroadcaster broadcaster;

    public RealtimeDispatcher(RealtimeBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        broadcaster.sendToUser(event.recipientUsername(), "/queue/notifications", event.dto());
        broadcaster.sendToUser(event.recipientUsername(), "/queue/unread-count", event.unreadCount());
    }
}
