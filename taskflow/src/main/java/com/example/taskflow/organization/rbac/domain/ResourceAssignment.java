package com.example.taskflow.organization.rbac.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Optional narrowing of a {@link RolePermissionScope} to specific resource instances.
 *
 * <p>When no {@code ResourceAssignment} rows exist for a given {@code RolePermissionScope},
 * the permission applies to <b>all</b> resources within the scope.
 * When rows exist, the permission applies <b>only</b> to the specified resources.
 *
 * <p>Example: A Team Lead's TEAM_UPDATE permission can be narrowed to specific team IDs.
 */
@Entity
@Table(name = "resource_assignments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ra_rps_type_id",
            columnNames = {"role_permission_scope_id", "resource_type", "resource_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ResourceAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_permission_scope_id", nullable = false)
    private RolePermissionScope rolePermissionScope;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;
}