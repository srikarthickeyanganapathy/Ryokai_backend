package com.example.taskflow.domain;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "organization_memberships", uniqueConstraints = {
    @UniqueConstraint(name = "uk_membership_user_org", columnNames = {"user_id", "organization_id"}),
    @UniqueConstraint(name = "uk_membership_one_org_per_user", columnNames = {"user_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class OrganizationMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    // EAGER because orgRole is always accessed during @PreAuthorize permission checks
    // which run outside @Transactional — LAZY would cause LazyInitializationException
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "org_role_id", nullable = false)
    private Role orgRole;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}
