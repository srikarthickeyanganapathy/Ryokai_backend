package com.example.taskflow.task.application.execution;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.notification.domain.Notification;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ExecutionContext {
    private Task focusRecommendation;
    private List<Task> queueOrdering;
    private List<Notification> interrupts;
    private List<Task> suggestedActions;
    private String resumeContext;
}
