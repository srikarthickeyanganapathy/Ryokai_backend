package com.example.taskflow.notification.application;

import com.example.taskflow.notification.domain.Notification;
import com.example.taskflow.notification.domain.PriorityTier;
import com.example.taskflow.notification.event.NotificationEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SignalGeneratorService {

    public PriorityTier determineTier(NotificationEvent event) {
        return switch (event) {
            case TASK_BLOCKED, ACCOUNT_SECURITY_ALERT, SESSION_REVOKED -> PriorityTier.INTERRUPT;
            case TASK_ASSIGNED, TASK_DUE_SOON, TASK_OVERDUE, LEAVE_REQUESTED -> PriorityTier.NOTIFICATION;
            case TASK_COMMENTED, CHECKLIST_UPDATED, ORG_MEMBER_JOINED, TASK_SUBMITTED, TASK_APPROVED, TASK_REJECTED, DEPENDENCY_RESOLVED -> PriorityTier.ACTIVITY;
            default -> PriorityTier.ARCHIVE;
        };
    }

    public List<Notification> getInterrupts(List<Notification> activeNotifications) {
        return activeNotifications.stream()
                .filter(n -> determineTier(n.getType()) == PriorityTier.INTERRUPT)
                .collect(Collectors.toList());
    }

    public List<Notification> getActiveSignals(List<Notification> activeNotifications) {
        return activeNotifications.stream()
                .filter(n -> determineTier(n.getType()) != PriorityTier.ARCHIVE)
                .collect(Collectors.toList());
    }
}
