package com.example.taskflow.organization.rbac.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit log for authorization decisions.
 * Records every GRANT and DENY decision with the reason for denial.
 *
 * <p>This table is write-heavy and read-seldom (admin/debug use only).
 * Consider partitioning by {@code evaluatedAt} for production deployments.
 */
@Entity
@Table(name = "permission_audit_log", indexes = {
    @Index(name = "idx_pal_user", columnList = "user_id"),
    @Index(name = "idx_pal_decision", columnList = "decision"),
    @Index(name = "idx_pal_evaluated", columnList = "evaluated_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PermissionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_code", nullable = false, length = 80)
    private String permissionCode;

    @Column(name = "resource_type", length = 20)
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    /** GRANT or DENY */
    @Column(nullable = false, length = 10)
    private String decision;

    /** Which pipeline stage denied: SCOPE, POLICY, FIELD, OVERRIDE, PERMISSION */
    @Column(name = "deny_reason", length = 100)
    private String denyReason;

    @CreationTimestamp
    @Column(name = "evaluated_at", updatable = false)
    private LocalDateTime evaluatedAt;
}