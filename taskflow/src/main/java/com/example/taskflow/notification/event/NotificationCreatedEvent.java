package com.example.taskflow.notification.event;

import com.example.taskflow.notification.dto.NotificationDTO;

public record NotificationCreatedEvent(
        NotificationDTO dto,
        String recipientUsername,
        long unreadCount
) {}
