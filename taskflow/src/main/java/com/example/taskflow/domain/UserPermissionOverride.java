package com.example.taskflow.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Direct permission grant or denial for an individual user,
 * overriding role-based permissions.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Temporary elevated access (developer needs TASK_OVERRIDE for one sprint)</li>
 *   <li>Explicit denial (suspend a user's MEMBER_INVITE without changing their role)</li>
 *   <li>Time-boxed overrides via {@code expiresAt}</li>
 * </ul>
 *
 * <p>Evaluation order:
 * <ol>
 *   <li>DENY override → immediately deny, skip role checks</li>
 *   <li>GRANT override → skip role checks, proceed to policy evaluation</li>
 *   <li>No override → fall through to role-based evaluation</li>
 * </ol>
 */
@Entity
@Table(name = "user_permission_overrides", uniqueConstraints = {
    @UniqueConstraint(name = "uq_upo_user_org_perm_scope",
            columnNames = {"user_id", "organization_id", "permission_id", "scope_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class UserPermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    private Scope scope;

    /** GRANT or DENY */
    @Column(name = "override_type", nullable = false, length = 10)
    private String overrideType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by", nullable = false)
    private User grantedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** NULL = permanent override. Non-null = auto-expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
