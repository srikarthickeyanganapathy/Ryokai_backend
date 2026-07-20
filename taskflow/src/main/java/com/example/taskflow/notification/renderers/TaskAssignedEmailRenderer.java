package com.example.taskflow.notification.renderers;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.notification.NotificationEmailRenderer;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.service.EmailService;
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
