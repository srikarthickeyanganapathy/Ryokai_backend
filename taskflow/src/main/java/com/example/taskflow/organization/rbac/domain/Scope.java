package com.example.taskflow.organization.rbac.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Authorization scope levels.
 * Determines <b>where</b> a permission applies within the organization hierarchy.
 *
 * <p>Scope hierarchy: ORGANIZATION âŠ‡ TEAM âŠ‡ PROJECT âŠ‡ OWN
 */
@Entity
@Table(name = "scopes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Scope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private Integer priority;
}