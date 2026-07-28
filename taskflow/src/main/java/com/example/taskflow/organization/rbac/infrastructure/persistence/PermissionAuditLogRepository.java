package com.example.taskflow.organization.rbac.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.organization.rbac.domain.PermissionAuditLog;

@Repository
public interface PermissionAuditLogRepository extends JpaRepository<PermissionAuditLog, Long> {
}
