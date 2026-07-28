package com.example.taskflow.audit.domain;

public enum AuditEventSource {
    API,
    SYSTEM,
    SCHEDULER,
    IMPORT,
    WEBSOCKET,
    MIGRATION,
    WEBHOOK
}
