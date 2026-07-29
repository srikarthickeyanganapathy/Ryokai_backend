package com.example.taskflow.audit.infrastructure.persistence;

import com.example.taskflow.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
