package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.notification.NotificationEvent;

@Service
public class TaskNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(TaskNotificationService.class);
    
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${app.reminders.timezone:Asia/Kolkata}")
    private String timezoneProperty;

    private ZoneId zoneId;
    private final Counter dueSoonSent;
    private final Counter dueSoonFailed;
    private final Counter overdueSent;
    private final Counter overdueFailed;

    public TaskNotificationService(TaskRepository taskRepository, NotificationService notificationService, MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.dueSoonSent = Counter.builder("taskflow.reminders").tag("type", "due-soon").tag("status", "sent").register(meterRegistry);
        this.dueSoonFailed = Counter.builder("taskflow.reminders").tag("type", "due-soon").tag("status", "failed").register(meterRegistry);
        this.overdueSent = Counter.builder("taskflow.reminders").tag("type", "overdue").tag("status", "sent").register(meterRegistry);
        this.overdueFailed = Counter.builder("taskflow.reminders").tag("type", "overdue").tag("status", "failed").register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezoneProperty);
    }

    @Transactional(readOnly = true)
    @Scheduled(cron = "${app.reminders.due-soon-cron:0 0 8 * * ?}", zone = "${app.reminders.timezone:Asia/Kolkata}")
    public void sendDueDateReminders() {
        if (!notificationsEnabled) return;
        
        logger.info("Starting due-date reminder job");
        int page = 0;
        int size = 100;
        long totalSent = 0;
        
        try {
            Page<Task> taskPage;
            do {
                taskPage = taskRepository.findDueSoon(
                    java.time.LocalDate.now(zoneId),
                    java.time.LocalDate.now(zoneId).plusDays(1),
                    List.of(TaskStatus.APPROVED),
                    PageRequest.of(page, size)
                );
                
                for (Task task : taskPage.getContent()) {
                    if (task.getAssignee() == null) continue;
                    
                    try {
                        notificationService.createAndSend(
                            task.getAssignee(),
                            null, // self-exclusion doesn't apply for reminders
                            NotificationEvent.TASK_DUE_SOON,
                            "Task due soon: " + task.getTitle(),
                            "Your task is due in less than 24 hours.",
                            task,
                            "reminder:" + task.getId() + ":" + java.time.LocalDate.now(zoneId)
                        );
                        totalSent++;
                        dueSoonSent.increment();
                    } catch (Exception e) {
                        logger.error("Failed to send reminder for task {}", task.getId(), e);
                        dueSoonFailed.increment();
                    }
                }
                page++;
            } while (!taskPage.isLast() && taskPage.hasNext());
        } catch (Exception e) {
            logger.error("Due-date reminder job failed at page {}", page, e);
        }
        
        logger.info("Due-date reminder job completed. Sent {} notifications", totalSent);
    }

    @Transactional(readOnly = true)
    @Scheduled(cron = "${app.reminders.overdue-cron:0 0 9 * * ?}", zone = "${app.reminders.timezone:Asia/Kolkata}")
    public void sendOverdueReminders() {
        if (!notificationsEnabled) return;
        
        logger.info("Starting overdue reminder job");
        int page = 0;
        int size = 100;
        long totalSent = 0;
        
        try {
            Page<Task> taskPage;
            do {
                taskPage = taskRepository.findOverdue(
                    java.time.LocalDate.now(zoneId),
                    List.of(TaskStatus.APPROVED),
                    PageRequest.of(page, size)
                );
                
                for (Task task : taskPage.getContent()) {
                    if (task.getAssignee() == null) continue;
                    
                    try {
                        notificationService.createAndSend(
                            task.getAssignee(),
                            null, // self-exclusion doesn't apply for reminders
                            NotificationEvent.TASK_OVERDUE,
                            "Task overdue: " + task.getTitle(),
                            "Your task is overdue.",
                            task,
                            "overdue:" + task.getId() + ":" + java.time.LocalDate.now(zoneId)
                        );
                        totalSent++;
                        overdueSent.increment();
                    } catch (Exception e) {
                        logger.error("Failed to send overdue reminder for task {}", task.getId(), e);
                        overdueFailed.increment();
                    }
                }
                page++;
            } while (!taskPage.isLast() && taskPage.hasNext());
        } catch (Exception e) {
            logger.error("Overdue reminder job failed at page {}", page, e);
        }
        
        logger.info("Overdue reminder job completed. Sent {} notifications", totalSent);
    }
}
