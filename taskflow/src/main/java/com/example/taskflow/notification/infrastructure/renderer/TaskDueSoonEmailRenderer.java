package com.example.taskflow.notification.infrastructure.renderer;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.integration.email.EmailService;
import org.springframework.stereotype.Component;

@Component
public class TaskDueSoonEmailRenderer implements NotificationEmailRenderer {

    private final EmailService emailService;

    public TaskDueSoonEmailRenderer(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean supports(NotificationEvent event) {
        return event == NotificationEvent.TASK_DUE_SOON;
    }

    @Override
    public void renderAndSend(User recipient, User actor, Task task, String dedupKey) {
        if (task != null) {
            emailService.sendDueDateReminder(
                    recipient.getEmail(), 
                    recipient.getUsername(), 
                    task.getTitle(), 
                    task.getId(), 
                    task.getDueDate(), 
                    24
            );
        }
    }
}
