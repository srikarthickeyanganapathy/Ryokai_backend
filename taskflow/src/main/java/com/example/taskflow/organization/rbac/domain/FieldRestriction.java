package com.example.taskflow.organization.rbac.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Field-level access control per role and resource type.
 *
 * <p>Instead of creating permissions like {@code PROJECT_UPDATE_NAME},
 * a single {@code PROJECT_UPDATE} permission is combined with field restriction rules.
 *
 * <p>Access levels:
 * <ul>
 *   <li>{@code ALLOW} — field can be read and written</li>
 *   <li>{@code DENY} — field cannot be written (write rejected with 403)</li>
 *   <li>{@code READ_ONLY} — field can be read but not written</li>
 * </ul>
 *
 * <p>When no restriction exists for a field, the default is ALLOW.
 */
@Entity
@Table(name = "field_restrictions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_fr_role_resource_field",
            columnNames = {"role_id", "resource_type", "field_name"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class FieldRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "resource_type", nullable = false, length = 40)
    private String resourceType;

    @Column(name = "field_name", nullable = false, length = 60)
    private String fieldName;

    @Column(name = "access_level", nullable = false, length = 20)
    private String accessLevel;
}
