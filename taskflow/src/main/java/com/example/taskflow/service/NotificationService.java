package com.example.taskflow.service;

import com.example.taskflow.domain.Notification;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.NotificationDTO;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.repository.NotificationRepository;
import com.example.taskflow.exception.UnauthorizedActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RealtimeBroadcaster broadcaster;
    private final com.example.taskflow.event.DomainEventPublisher domainEventPublisher;
    private final java.util.List<com.example.taskflow.notification.NotificationEmailRenderer> emailRenderers;

    @Transactional
    public void createAndSend(User recipient, User excludeUser, NotificationEvent type,
                              String title, String message, Task task, String dedupKey, User actor) {
        
        if (recipient == null) {
            return;
        }
        
        if (excludeUser != null && recipient.getId().equals(excludeUser.getId())) {
            return;
        }
        
        if (dedupKey != null) {
            LocalDateTime fiveMinsAgo = LocalDateTime.now().minusMinutes(5);
            boolean exists = notificationRepository.existsByUserIdAndDeduplicationKeyAndCreatedAtAfter(
                    recipient.getId(), dedupKey, fiveMinsAgo);
            if (exists) {
                log.debug("Skipping duplicate notification for user {}, key {}", recipient.getId(), dedupKey);
                return;
            }
        }
        
        Notification n = new Notification();
        n.setUser(recipient);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setActor(actor);
        if (task != null) {
            n.setTaskId(task.getId());
            n.setTaskTitleSnapshot(task.getTitle());
        }
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        n.setDeduplicationKey(dedupKey);
        
        n = notificationRepository.save(n);
        
        NotificationDTO dto = toDTO(n);
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(recipient.getId());
        
        domainEventPublisher.publish(new com.example.taskflow.notification.NotificationCreatedEvent(dto, recipient.getUsername(), unreadCount));
        
        if (recipient.isEmailNotificationsEnabled() && recipient.getEmail() != null) {
            log.info("Sending email notification to {}", recipient.getEmail());
            emailRenderers.stream()
                .filter(r -> r.supports(type))
                .findFirst()
                .ifPresent(r -> r.renderAndSend(recipient, actor, task, dedupKey));
        }
    }
    
    @Transactional
    public void createAndSend(User recipient, User excludeUser, NotificationEvent type,
                              String title, String message, Task task, String dedupKey) {
        createAndSend(recipient, excludeUser, type, title, message, task, dedupKey, null);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getUserNotifications(User user, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public void markAsRead(User user, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("Unauthorized to mark this notification");
        }
        
        notification.setRead(true);
        notificationRepository.save(notification);
        
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(user.getId());
        broadcaster.sendToUser(user.getUsername(), "/queue/unread-count", unreadCount);
    }

    @Transactional
    public void markAllAsRead(User user) {
        notificationRepository.markAllAsRead(user.getId());
        broadcaster.sendToUser(user.getUsername(), "/queue/unread-count", 0L);
    }

    @Transactional
    public void deleteNotification(User user, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("Unauthorized to delete this notification");
        }
        
        notificationRepository.delete(notification);
        
        if (!notification.isRead()) {
            long unreadCount = notificationRepository.countByUserIdAndReadFalse(user.getId());
            broadcaster.sendToUser(user.getUsername(), "/queue/unread-count", unreadCount);
        }
    }

    private NotificationDTO toDTO(Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getMessage(),
                n.getTaskId(),
                n.getTaskTitleSnapshot(),
                n.isRead(),
                n.getCreatedAt(),
                NotificationDTO.getRelativeTime(n.getCreatedAt()),
                n.getDeduplicationKey(),
                n.getActor() != null ? n.getActor().getUsername() : null
        );
    }
}
