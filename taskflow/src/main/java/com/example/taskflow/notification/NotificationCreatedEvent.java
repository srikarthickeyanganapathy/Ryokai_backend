package com.example.taskflow.notification;

import com.example.taskflow.dto.NotificationDTO;

public record NotificationCreatedEvent(
        NotificationDTO dto,
        String recipientUsername,
        long unreadCount
) {}
