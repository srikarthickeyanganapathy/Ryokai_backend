package com.example.taskflow.util;

import java.time.Duration;
import java.time.LocalDateTime;

public class RelativeTimeFormatter {

    public static String format(LocalDateTime past) {
        if (past == null) return "";
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(past, now);

        if (duration.isNegative()) {
            return "in the future";
        }

        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return "just now";
        }

        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + "m ago";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }

        long days = duration.toDays();
        if (days < 7) {
            return days + "d ago";
        }

        long weeks = days / 7;
        if (days < 30) {
            return weeks + "w ago";
        }

        long months = days / 30;
        if (days < 365) {
            return months + "mo ago";
        }

        long years = days / 365;
        return years + "y ago";
    }
}
