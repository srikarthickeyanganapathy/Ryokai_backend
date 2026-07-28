package com.example.taskflow.organization.rbac.domain;

import jakarta.persistence.*;
import lombok.*;
import com.example.taskflow.organization.core.domain.Organization;

/**
 * Core junction table linking a Role to a Permission at a specific Scope.
 *
 * <p>Replaces the legacy {@code role_permissions} join table by adding scope awareness.
 * The same role can have different permissions at different scopes:
 * e.g., TASK_VIEW at ORGANIZATION scope but TASK_UPDATE at OWN scope only.
 */
@Entity
@Table(name = "role_permission_scopes", uniqueConstraints = {
    @UniqueConstraint(name = "uq_rps_role_perm_scope",
            columnNames = {"role_id", "permission_id", "scope_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RolePermissionScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    private Scope scope;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionScope)) return false;
        RolePermissionScope that = (RolePermissionScope) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}