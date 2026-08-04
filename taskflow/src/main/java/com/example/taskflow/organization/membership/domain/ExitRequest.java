package com.example.taskflow.organization.membership.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.user.domain.User;

@Entity
@Table(name = "exit_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ExitRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExitRequestStatus status = ExitRequestStatus.PENDING;

    @Column(name = "decision_comment", columnDefinition = "TEXT")
    private String decisionComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "effective_exit_date")
    private LocalDate effectiveExitDate;

    public User getRequestedBy() {
        return user;
    }

    public void setRequestedBy(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return requestedAt;
    }

    public enum ExitRequestStatus {
        PENDING,
        APPROVED,
        OFFBOARDING,
        COMPLETED,
        REJECTED,
        CANCELLED
    }
}
