package com.example.taskflow.notification.infrastructure.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.taskflow.integration.email.EmailService;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;

@Component
public class LeaveRequestedEmailRenderer implements NotificationEmailRenderer {

    private static final Logger log = LoggerFactory.getLogger(LeaveRequestedEmailRenderer.class);
    private final EmailService emailService;

    public LeaveRequestedEmailRenderer(EmailService emailService) {
        this.emailService = emailService;
    }

    @Override
    public boolean supports(NotificationEvent event) {
        return event == NotificationEvent.LEAVE_REQUESTED;
    }

    @Override
    public void renderAndSend(User recipient, User actor, Task task, String dedupKey) {
        if (actor != null) {
            try {
                String requestIdStr = dedupKey != null && dedupKey.startsWith("leave-request:") ? dedupKey.split(":")[1] : "0";
                Long requestId = Long.parseLong(requestIdStr);
                emailService.sendLeaveRequestEmail(
                        recipient.getEmail(), 
                        recipient.getUsername(), 
                        actor.getUsername(), 
                        "Your Organization", 
                        requestId
                );
            } catch (Exception e) {
                log.error("Failed to parse leave request id for email", e);
            }
        }
    }
}