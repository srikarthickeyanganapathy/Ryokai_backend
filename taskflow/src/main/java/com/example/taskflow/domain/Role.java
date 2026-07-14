package com.example.taskflow.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

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
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ====================================================================
    // Name-based role hierarchy helpers (replaces RoleCategory enum)
    // ====================================================================

    /**
     * Returns true if this role is the builtin ADMIN role.
     */
    public boolean isBuiltinAdmin() {
        return "ADMIN".equals(name);
    }

    /**
     * Returns true if this role is ADMIN or DIRECTOR (i.e., director-level or above).
     */
    public boolean isBuiltinDirectorOrAbove() {
        return "ADMIN".equals(name) || "DIRECTOR".equals(name);
    }

    /**
     * Returns true if this role is ADMIN, DIRECTOR, or MANAGER (i.e., manager-level or above).
     */
    public boolean isBuiltinManagerOrAbove() {
        return "ADMIN".equals(name) || "DIRECTOR".equals(name) || "MANAGER".equals(name);
    }
}
