package com.example.taskflow.audit.infrastructure.persistence;

import com.example.taskflow.audit.domain.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
}
