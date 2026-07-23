package com.example.taskflow.domain;

public enum AuditEventSource {
    API,
    SYSTEM,
    SCHEDULER,
    IMPORT,
    WEBSOCKET,
    MIGRATION,
    WEBHOOK
}
