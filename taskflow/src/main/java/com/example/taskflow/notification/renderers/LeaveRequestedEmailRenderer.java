package com.example.taskflow.notification.renderers;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.notification.NotificationEmailRenderer;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.service.EmailService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
