package com.example.taskflow.notification.infrastructure.renderer;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.notification.infrastructure.renderer.NotificationEmailRenderer;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.integration.email.EmailService;
import org.springframework.stereotype.Component;

@Component
public class TaskRejectedEmailRenderer implements NotificationEmailRenderer {

    private final EmailService emailService;

    public TaskRejectedEmailRenderer(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean supports(NotificationEvent event) {
        return event == NotificationEvent.TASK_REJECTED;
    }

    @Override
    public void renderAndSend(User recipient, User actor, Task task, String dedupKey) {
        if (task != null) {
            emailService.sendTaskReviewNotification(
                    recipient.getEmail(), 
                    recipient.getUsername(), 
                    task.getTitle(), 
                    task.getId(), 
                    "REJECTED", 
                    actor != null ? actor.getUsername() : "Reviewer", 
                    "See dashboard for details"
            );
        }
    }
}
