package com.example.taskflow.notification.renderers;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.notification.NotificationEmailRenderer;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.service.EmailService;
import org.springframework.stereotype.Component;

@Component
public class TaskApprovedEmailRenderer implements NotificationEmailRenderer {

    private final EmailService emailService;

    public TaskApprovedEmailRenderer(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean supports(NotificationEvent event) {
        return event == NotificationEvent.TASK_APPROVED;
    }

    @Override
    public void renderAndSend(User recipient, User actor, Task task, String dedupKey) {
        if (task != null) {
            emailService.sendTaskReviewNotification(
                    recipient.getEmail(), 
                    recipient.getUsername(), 
                    task.getTitle(), 
                    task.getId(), 
                    "APPROVED", 
                    actor != null ? actor.getUsername() : "Reviewer", 
                    null
            );
        }
    }
}
