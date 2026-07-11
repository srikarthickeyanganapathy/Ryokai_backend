package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record NotificationDTO(
        Long id,
        String type,
        String title,
        String message,
        Long taskId,
        String taskTitle,
        boolean read,
        LocalDateTime createdAt,
        String relativeTime,
        String deduplicationKey,
        String actorUsername
) {
    public static String getRelativeTime(LocalDateTime time) {
        if (time == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(time, now);
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = ChronoUnit.HOURS.between(time, now);
        if (hours < 24) return hours + "h ago";
        long days = ChronoUnit.DAYS.between(time, now);
        if (days < 7) return days + "d ago";
        return time.toLocalDate().toString();
    }
}
