package com.example.taskflow.domain;

import com.example.taskflow.notification.NotificationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_user_read", columnList = "user_id, read")
})
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationEvent type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 2000)
    private String message;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "task_title_snapshot", length = 255)
    private String taskTitleSnapshot;

    // ER-M01 fix: spec requires a `metadata jsonb` column on NOTIFICATIONS.
    // The column is created by V39 migration. The existing task_id /
    // task_title_snapshot / dedup_key columns are kept (they power the
    // realtime + dedup logic)  -  this metadata field is for additional
    // structured context per the spec.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "dedup_key", length = 100)
    private String deduplicationKey;
}
