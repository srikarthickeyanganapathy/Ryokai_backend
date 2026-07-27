package com.example.taskflow.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps runtime policy predicates to permissions.
 *
 * <p>After a permission check passes, the associated policies are evaluated
 * to determine if the operation should be allowed given the current context.
 *
 * <p>Example: {@code TASK_APPROVE} requires policies
 * {@code ORG_IS_ACTIVE AND RESOURCE_NOT_ARCHIVED AND TASK_STATUS_EQUALS(IN_REVIEW)}.
 */
@Entity
@Table(name = "permission_policies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PermissionPolicyMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Column(name = "policy_key", nullable = false, length = 60)
    private String policyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_params", columnDefinition = "jsonb")
    private String policyParams;

    @Column(nullable = false, length = 5)
    private String operator = "AND";

    @Column(name = "evaluation_order", nullable = false)
    private Integer evaluationOrder = 0;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;
}
