package com.example.taskflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Legacy identifier — preserved for backward compatibility. */
    @Column(unique = true, nullable = false, length = 60)
    private String name;

    /** Canonical permission identifier (matches {@code PermissionCode} enum). */
    @Column(unique = true, nullable = false, length = 80)
    private String code;

    /** Module this permission belongs to (e.g., TASK, PROJECT, ORGANIZATION). */
    @Column(length = 40)
    private String module;

    /** Action category (CRUD, WORKFLOW, LIFECYCLE, MEMBERSHIP, SETTINGS, EXPORT). */
    @Column(length = 20)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** System permissions cannot be deleted by admins. */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
