package com.example.taskflow.audit.infrastructure.persistence;

import com.example.taskflow.audit.domain.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, Long> {
}
