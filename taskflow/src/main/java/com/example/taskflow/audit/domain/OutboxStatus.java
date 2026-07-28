package com.example.taskflow.audit.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}
