package com.example.taskflow.notification.renderers;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.notification.NotificationEmailRenderer;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.service.EmailService;
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
