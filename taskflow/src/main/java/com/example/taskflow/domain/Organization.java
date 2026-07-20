package com.example.taskflow.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "organizations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Organization {

    public enum OrgStatus { ACTIVE, SUSPENDED, DELETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 100)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgStatus status = OrgStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<OrganizationMembership> memberships = new HashSet<>();

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<Team> teams = new HashSet<>();

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<Role> roles = new HashSet<>();

    public void requireActive() {
        if (this.status != OrgStatus.ACTIVE) {
            throw new com.example.taskflow.exception.OrganizationSuspendedException(
                    "Organization '" + this.name + "' is " + this.status.name().toLowerCase()
                    + ". All operations are restricted until it is reactivated.");
        }
    }

    public void ensureNotLastAdmin(User user) {
        long adminCount = this.memberships.stream()
                .filter(m -> m.getOrgRole() != null && m.getOrgRole().isBuiltinAdmin())
                .count();
        boolean isAdmin = this.memberships.stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()) && m.getOrgRole() != null && m.getOrgRole().isBuiltinAdmin());
        if (isAdmin && adminCount <= 1) {
            throw new IllegalStateException(
                    "Cannot remove the last Admin of the organization. Promote another member to Admin first.");
        }
    }
}
