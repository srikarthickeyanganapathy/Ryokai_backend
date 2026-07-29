package com.example.taskflow.organization.rbac.domain;


import com.example.taskflow.organization.core.domain.Organization;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roles", uniqueConstraints = {
    // RB-C02 fix: composite unique on (name, organization_id) replaces the
    // V1 single-column UNIQUE on name. Allows per-org builtin roles
    // (ADMIN/DIRECTOR/MANAGER/EMPLOYEE) to coexist with the global builtin
    // rows seeded by V19/V27. Mirrors V39 migration's uq_roles_name_org index.
    @UniqueConstraint(name = "uq_roles_name_org", columnNames = {"name", "organization_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RolePermissionScope> rolePermissionScopes = new HashSet<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin = false;

    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    /** System roles cannot be deleted or have their permissions modified. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    /** Highest scope this role can grant. Prevents privilege escalation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "max_scope_id")
    private Scope maxScope;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ====================================================================
    // DEPRECATED: Name-based role hierarchy helpers
    // These will be removed in Phase 4. Use PermissionCode checks instead.
    // ====================================================================

    /**
     * @deprecated Use explicit permission checks via the authorization pipeline.
     */
    @Deprecated(forRemoval = true)
    public boolean isBuiltinAdmin() {
        return "ADMIN".equals(name);
    }

    /**
     * @deprecated Use explicit permission checks via the authorization pipeline.
     */
    @Deprecated(forRemoval = true)
    public boolean isBuiltinDirectorOrAbove() {
        return "ADMIN".equals(name) || "DIRECTOR".equals(name);
    }

    /**
     * @deprecated Use explicit permission checks via the authorization pipeline.
     */
    @Deprecated(forRemoval = true)
    public boolean isBuiltinManagerOrAbove() {
        return "ADMIN".equals(name) || "DIRECTOR".equals(name) || "MANAGER".equals(name);
    }
}
