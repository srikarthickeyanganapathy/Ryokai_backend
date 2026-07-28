package com.example.taskflow.notification.infrastructure.renderer;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.notification.infrastructure.renderer.NotificationEmailRenderer;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.integration.email.EmailService;
import org.springframework.stereotype.Component;

@Component
public class TaskAssignedEmailRenderer implements NotificationEmailRenderer {

    private final EmailService emailService;

    public TaskAssignedEmailRenderer(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean supports(NotificationEvent event) {
        return event == NotificationEvent.TASK_ASSIGNED;
    }

    @Override
    public void renderAndSend(User recipient, User actor, Task task, String dedupKey) {
        if (task != null) {
            emailService.sendTaskAssignmentNotification(
                    recipient.getEmail(), 
                    recipient.getUsername(), 
                    task.getTitle(), 
                    task.getId(), 
                    actor != null ? actor.getUsername() : "System", 
                    task.getDueDate()
            );
        }
    }
}
