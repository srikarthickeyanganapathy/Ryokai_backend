package com.example.taskflow.organization.rbac.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.organization.rbac.domain.PermissionAuditLog;

public interface PermissionAuditLogRepository extends JpaRepository<PermissionAuditLog, Long> {
}
