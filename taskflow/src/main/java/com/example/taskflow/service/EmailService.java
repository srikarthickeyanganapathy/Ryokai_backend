package com.example.taskflow.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.example.taskflow.domain.User;
import com.example.taskflow.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final UserRepository userRepository;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:noreply@taskflow.local}")
    private String fromAddress;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.expiry-minutes:60}")
    private int resetExpiryMinutes;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.userRepository = userRepository;
    }

    /**
     * SEC-M02 fix: helper to check whether a recipient should receive
     * NON-SECURITY notification emails (task assignments, reviews, reminders,
     * leave requests). Spec implies these should be gated on BOTH:
     *   - email_verified = true
     *   - email_notifications_enabled = true
     *
     * Security-critical emails (password reset, password changed, email
     * verification) are NOT gated  -  they must always be delivered.
     */
    public boolean shouldSendNotificationTo(String email) {
        if (email == null || email.isBlank()) return false;
        return userRepository.findByEmail(email)
            .map(User::isEmailNotificationsEnabled)
            .orElse(false);
    }

    public boolean shouldSendNotificationTo(User user) {
        if (user == null) return false;
        // SEC-M02: both flags must be true for non-security emails.
        // email_verified is enforced here (was previously unchecked);
        // email_notifications_enabled was already checked at the call site
        // but we double-check for safety.
        return user.isEmailVerified() && user.isEmailNotificationsEnabled();
    }

    // ============================================================
    // SECURITY-CRITICAL EMAILS  -  never gated on email_verified / email_notifications_enabled.
    // These MUST be delivered: password reset, password changed, email verification.
    // ============================================================

    @Async("emailExecutor")
    public void sendPasswordResetEmail(String toEmail, String toName, String resetLink) {
        if (!emailEnabled) {
            log.info("Email disabled. Would have sent password reset to {}", toEmail);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", toName);
            ctx.setVariable("resetLink", resetLink);
            ctx.setVariable("expirationHours", Math.max(1, resetExpiryMinutes / 60));
            ctx.setVariable("frontendUrl", frontendUrl);

            String html = templateEngine.process("email/password-reset", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  Reset your password", html);
        } catch (Exception e) {
            log.error("SMTP failure: Failed to send password reset email to {}", toEmail, e);
            throw new RuntimeException("SMTP failure", e);
        }
    }

    @Async("emailExecutor")
    public void sendEmailVerification(String toEmail, String toName, String token) {
        if (!emailEnabled) {
            log.info("Email disabled. Would have sent verification email to {}", toEmail);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", toName);
            ctx.setVariable("verifyLink", frontendUrl + "/verify-email?token=" + token);

            String html = templateEngine.process("email/verify-email", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  Verify your email", html);
        } catch (Exception e) {
            log.error("SMTP failure: Failed to send verification email to {}", toEmail, e);
        }
    }

    @Async("emailExecutor")
    public void sendPasswordChangedNotification(String toEmail, String toName, String ipAddress) {
        if (!emailEnabled) return;
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", toName);
            ctx.setVariable("timestamp", LocalDateTime.now());
            ctx.setVariable("ipAddress", ipAddress);

            String html = templateEngine.process("email/password-changed", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  Password Changed", html);
        } catch (Exception e) {
            log.error("SMTP failure: Failed to send password changed email to {}", toEmail, e);
            throw new RuntimeException("SMTP failure", e);
        }
    }

    // ============================================================
    // NOTIFICATION EMAILS  -  gated on email_verified AND email_notifications_enabled.
    // SEC-M02 fix: previously these were sent regardless of either flag.
    // ============================================================

    @Async("emailExecutor")
    public void sendTaskAssignmentNotification(String toEmail, String assigneeName, String taskTitle, Long taskId, String assignerName, java.time.LocalDate dueDate) {
        if (!emailEnabled) return;
        // SEC-M02 fix: gate on both flags
        if (!shouldSendNotificationTo(toEmail)) {
            log.debug("Skipping task assignment email to {} (email_verified or email_notifications_enabled is false)", toEmail);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("assigneeName", assigneeName);
            ctx.setVariable("taskTitle", taskTitle);
            ctx.setVariable("taskUrl", frontendUrl + "/board?task=" + taskId);
            ctx.setVariable("assignerName", assignerName);
            ctx.setVariable("dueDate", dueDate);

            String html = templateEngine.process("email/task-assigned", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  New Task Assigned", html);
        } catch (Exception e) {
            log.error("Failed to send task assignment email to {}", toEmail, e);
        }
    }

    @Async("emailExecutor")
    public void sendTaskReviewNotification(String toEmail, String assigneeName, String taskTitle, Long taskId, String status, String reviewerName, String reason) {
        if (!emailEnabled) return;
        // SEC-M02 fix: gate on both flags
        if (!shouldSendNotificationTo(toEmail)) {
            log.debug("Skipping task review email to {} (email_verified or email_notifications_enabled is false)", toEmail);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("assigneeName", assigneeName);
            ctx.setVariable("taskTitle", taskTitle);
            ctx.setVariable("taskUrl", frontendUrl + "/board?task=" + taskId);
            ctx.setVariable("status", status);
            ctx.setVariable("reviewerName", reviewerName);
            ctx.setVariable("reason", reason);

            String html = templateEngine.process("email/task-reviewed", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  Task Review " + status, html);
        } catch (Exception e) {
            log.error("Failed to send task review email to {}", toEmail, e);
        }
    }

    @Async("emailExecutor")
    public void sendDueDateReminder(String toEmail, String assigneeName, String taskTitle, Long taskId, java.time.LocalDate dueDate, int hoursUntilDue) {
        if (!emailEnabled) return;
        // SEC-M02 fix: gate on both flags
        if (!shouldSendNotificationTo(toEmail)) {
            log.debug("Skipping due date reminder email to {} (email_verified or email_notifications_enabled is false)", toEmail);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("assigneeName", assigneeName);
            ctx.setVariable("taskTitle", taskTitle);
            ctx.setVariable("taskUrl", frontendUrl + "/board?task=" + taskId);
            ctx.setVariable("dueDate", dueDate);
            ctx.setVariable("hoursUntilDue", hoursUntilDue);

            String html = templateEngine.process("email/due-date-reminder", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  Task Due Soon Reminder", html);
        } catch (Exception e) {
            log.error("Failed to send due date reminder email to {}", toEmail, e);
        }
    }

    @Async("emailExecutor")
    public void sendLeaveRequestEmail(String toEmail, String adminName, String userName, String orgName, Long requestId) {
        if (!emailEnabled) return;
        // SEC-M02 fix: gate on both flags
        if (!shouldSendNotificationTo(toEmail)) {
            log.debug("Skipping leave request email to {} (email_verified or email_notifications_enabled is false)", toEmail);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("adminName", adminName);
            ctx.setVariable("userName", userName);
            ctx.setVariable("orgName", orgName);
            ctx.setVariable("requestUrl", frontendUrl + "/org/leave-requests?id=" + requestId);

            String html = templateEngine.process("email/leave-request", ctx);
            sendHtmlEmail(toEmail, "TaskFlow  -  Leave Request from " + userName, html);
        } catch (Exception e) {
            log.error("Failed to send leave request email to {}", toEmail, e);
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setReplyTo("no-reply@taskflow.local");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(msg);
        log.info("Email sent to {}: {}", to, subject);
    }
}
