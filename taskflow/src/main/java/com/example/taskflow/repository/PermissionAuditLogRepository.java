package com.example.taskflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.PermissionAuditLog;

@Repository
public interface PermissionAuditLogRepository extends JpaRepository<PermissionAuditLog, Long> {
}
