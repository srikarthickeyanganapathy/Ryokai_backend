package com.example.taskflow.util;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;
import io.micrometer.core.instrument.Gauge;

@Component
@RequiredArgsConstructor
public class TaskMetrics {
    private final MeterRegistry registry;

    // Counter — increments forever
    public void recordTaskCreated(String status, String priority) {
        registry.counter("taskflow_tasks_total",
            "status", status, "priority", priority).increment();
    }

    // Timer — for durations
    public void recordTaskCompletionDuration(long durationSeconds, String priority) {
        registry.timer("taskflow_task_duration_seconds",
            "priority", priority).record(Duration.ofSeconds(durationSeconds));
    }

    // Counter — auth attempts
    public void recordAuthAttempt(String result) {  // "success" or "failure"
        registry.counter("taskflow_auth_attempts_total", "result", result).increment();
    }

    // Gauge — current value (recomputed on scrape)
    public void registerActiveSessionsGauge(Supplier<Number> supplier) {
        Gauge.builder("taskflow_active_sessions", supplier).register(registry);
    }
}
