package com.example.taskflow.util;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Micrometer metrics helper component for domain & business KPIs.
 * Exposes counters, timers, and gauges scraped by Prometheus.
 */
@Component
@RequiredArgsConstructor
public class TaskMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger activeWebSocketConnections = new AtomicInteger(0);

    // Initializer to register custom gauges
    public void initGauges() {
        Gauge.builder("taskflow_websocket_connections_active", activeWebSocketConnections, AtomicInteger::get)
                .description("Active WebSocket STOMP sessions")
                .register(registry);
    }

    // --- Authentication & Session Metrics ---
    public void recordLoginSuccess() {
        registry.counter("taskflow_login_success_total").increment();
        registry.counter("taskflow_auth_attempts_total", "result", "success").increment();
    }

    public void recordLoginFailure(String reason) {
        registry.counter("taskflow_login_failure_total", "reason", reason != null ? reason : "unknown").increment();
        registry.counter("taskflow_auth_attempts_total", "result", "failure").increment();
    }

    public void recordRegistration() {
        registry.counter("taskflow_registrations_total").increment();
    }

    // --- Business KPIs ---
    public void recordOrganizationCreated() {
        registry.counter("taskflow_organizations_created_total").increment();
    }

    public void recordWorkspaceCreated(String type) {
        registry.counter("taskflow_workspaces_created_total", "type", type != null ? type : "default").increment();
    }

    public void recordProjectCreated(String scope) {
        registry.counter("taskflow_projects_created_total", "scope", scope != null ? scope : "ORGANIZATION").increment();
    }

    public void recordTaskCreated(String status, String priority) {
        registry.counter("taskflow_tasks_created_total", "status", status, "priority", priority).increment();
        registry.counter("taskflow_tasks_total", "status", status, "priority", priority).increment();
    }

    public void recordTaskCompleted(String priority) {
        registry.counter("taskflow_tasks_completed_total", "priority", priority != null ? priority : "MEDIUM").increment();
    }

    public void recordTaskCompletionDuration(long durationSeconds, String priority) {
        registry.timer("taskflow_task_duration_seconds", "priority", priority != null ? priority : "MEDIUM")
                .record(Duration.ofSeconds(durationSeconds));
    }

    public void recordFileUpload(String fileType) {
        registry.counter("taskflow_file_uploads_total", "file_type", fileType != null ? fileType : "UNKNOWN").increment();
    }

    // --- WebSocket Metrics ---
    public void incrementWebSocketConnections() {
        activeWebSocketConnections.incrementAndGet();
        registry.counter("taskflow_websocket_connections_total", "event", "connect").increment();
    }

    public void decrementWebSocketConnections() {
        if (activeWebSocketConnections.get() > 0) {
            activeWebSocketConnections.decrementAndGet();
        }
        registry.counter("taskflow_websocket_connections_total", "event", "disconnect").increment();
    }

    public void registerActiveSessionsGauge(Supplier<Number> supplier) {
        Gauge.builder("taskflow_active_sessions", supplier).register(registry);
    }
}
