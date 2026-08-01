package com.example.taskflow.task.application.execution;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.task.domain.model.TaskPriority;
import com.example.taskflow.notification.domain.Notification;
import com.example.taskflow.notification.domain.PriorityTier;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

@Service
public class ExecutionEngineService {

    public ExecutionContext getExecutionContext(List<Task> allTasks, List<Notification> activeNotifications, String resumeContext) {
        
        List<Notification> interrupts = activeNotifications.stream()
            .filter(n -> n.getType() == com.example.taskflow.notification.event.NotificationEvent.TASK_BLOCKED || n.getType() == com.example.taskflow.notification.event.NotificationEvent.ACCOUNT_SECURITY_ALERT || n.getType() == com.example.taskflow.notification.event.NotificationEvent.SESSION_REVOKED)
            .collect(Collectors.toList());

        List<Task> queueOrdering = allTasks.stream()
            .filter(t -> !t.getCurrentStatus().isTerminal())
            .sorted(Comparator.comparing((Task t) -> t.getPriority() != null ? t.getPriority().ordinal() : TaskPriority.LOW.ordinal()).reversed()
                .thenComparing(t -> t.getDueDate() != null ? t.getDueDate() : LocalDate.MAX))
            .collect(Collectors.toList());

        Task focus = determineFocus(queueOrdering, interrupts);

        return ExecutionContext.builder()
                .focusRecommendation(focus)
                .queueOrdering(queueOrdering)
                .interrupts(interrupts)
                .suggestedActions(queueOrdering.stream().limit(3).collect(Collectors.toList()))
                .resumeContext(resumeContext)
                .build();
    }

    private Task determineFocus(List<Task> queueOrdering, List<Notification> interrupts) {
        for (Notification interrupt : interrupts) {
            if (interrupt.getTaskId() != null) {
                return queueOrdering.stream()
                        .filter(t -> t.getId().equals(interrupt.getTaskId()))
                        .findFirst()
                        .orElse(null);
            }
        }
        
        Task approvalTask = queueOrdering.stream()
            .filter(t -> t.getCurrentStatus() == TaskStatus.SUBMITTED)
            .findFirst().orElse(null);
        if (approvalTask != null) return approvalTask;

        Task dueToday = queueOrdering.stream()
            .filter(t -> t.getDueDate() != null && t.getDueDate().isEqual(LocalDate.now()))
            .findFirst().orElse(null);
        if (dueToday != null) return dueToday;

        return queueOrdering.isEmpty() ? null : queueOrdering.get(0);
    }
}
