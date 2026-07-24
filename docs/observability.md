# Ryokai Backend — Full-Stack Observability Documentation

Back to **[Master Index](README.md)** | View **[Architecture Overview](architecture.md)** | View **[Operations Guide](operations.md)**

---

## 1. Observability Architecture Overview

The Ryokai Taskflow backend is instrumented with a production-grade **3D Observability Stack** covering **Metrics**, **Traces**, and **Logs** without modifying existing business logic or breaking API contracts.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SPRING BOOT BACKEND                                │
│                                                                             │
│   ┌───────────────────┐    ┌───────────────────┐    ┌───────────────────┐   │
│   │    Micrometer     │    │  OpenTelemetry    │    │  Logstash Encoder │   │
│   │    (Metrics)      │    │  OTLP Exporter    │    │   (JSON Logs)     │   │
│   └─────────┬─────────┘    └─────────┬─────────┘    └─────────┬─────────┘   │
└─────────────┼────────────────────────┼────────────────────────┼─────────────┘
              │                        │                        │
              ▼ (Scrape: 8080/actuator)│ (Push: 4318 OTLP)      ▼ (stdout / Docker)
       ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
       │  Prometheus  │         │Grafana Tempo │         │Grafana Alloy │
       └──────┬───────┘         └──────┬───────┘         └──────┬───────┘
              │ (Alerts)               │                        │ (Push: 3100)
              ▼                        │                        ▼
       ┌──────────────┐                │                 ┌──────────────┐
       │ Alertmanager │                │                 │ Grafana Loki │
       └──────────────┘                │                 └──────┬───────┘
              │                        │                        │
              └────────────────────────┼────────────────────────┘
                                       ▼
                               ┌──────────────┐
                               │   Grafana    │
                               │ (Dashboards) │
                               └──────────────┘
```

---

## 2. Correlation & Context Propagation

Every log line, metric, and trace is linked using structured MDC (Mapped Diagnostic Context) metadata:

| Key | Header / Source | Description |
| :--- | :--- | :--- |
| `correlationId` | `X-Correlation-Id` | Unique ID tracking a user request across microservice boundaries. |
| `traceId` | OpenTelemetry Tracer | 128-bit trace identifier generated per incoming HTTP request. |
| `spanId` | OpenTelemetry Tracer | 64-bit span identifier for the active execution segment. |
| `userId` | `JwtAuthenticationFilter` | ID of the authenticated user making the request. |
| `requestId` | `CorrelationIdFilter` | Request UUID assigned at HTTP ingress. |

### Automated MDC Log Enrichment
All JSON log entries published to container `stdout` in production automatically contain these fields:

```json
{
  "@timestamp": "2026-07-23T18:30:00.123+05:30",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger_name": "com.example.taskflow.controller.TaskController",
  "message": "Task #104 created successfully",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7",
  "userId": "42",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "application": "taskflow",
  "service.name": "taskflow",
  "environment": "prod"
}
```

---

## 3. Distributed Tracing (OpenTelemetry & Tempo)

### Auto-Instrumentation
OpenTelemetry automatically instruments:
- **Spring MVC**: Controller endpoints, path parameters, status codes.
- **Spring Security**: Authentication filters, authorization checks.
- **JDBC & HikariCP**: SQL queries, statement execution time, connection acquisition.
- **Hibernate**: Entity persistence, transactions, flush operations.
- **RestTemplate / WebClient**: Outbound HTTP requests with trace propagation headers (`traceparent`, `tracestate`).
- **Async & Scheduled Tasks**: Thread context propagation via `MdcTaskDecorator`.

### Exporting Traces
Traces are exported over OTLP/HTTP to Grafana Tempo at `http://tempo:4318/v1/traces`.

---

## 4. Log Aggregation (Loki & Grafana Alloy)

- **Engine**: Grafana Loki (`http://loki:3100`).
- **Shipper**: Grafana Alloy (`alloy.river`).
- **Log Processing**:
  1. Captures container `stdout` JSON logs from Docker socket `/var/run/docker.sock`.
  2. Parses JSON fields (`timestamp`, `level`, `traceId`, `spanId`, `correlationId`, `userId`).
  3. Enriches log records with labels: `application="taskflow"`, `service_name="taskflow"`, `environment="prod"`, `level`.
  4. Ships structured records to Loki.

---

## 5. Metrics & Custom Business KPIs (Micrometer & Prometheus)

Prometheus scrapes metrics from `/actuator/prometheus` every 5 seconds.

### System & Infrastructure Metrics
- **JVM & GC**: `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_sum`, `jvm_threads_live_threads`.
- **CPU**: `system_cpu_usage`, `process_cpu_usage`.
- **HikariCP**: `hikaricp_connections_active`, `hikaricp_connections_idle`, `hikaricp_connections_pending`.
- **HTTP**: `http_server_requests_seconds_count`, `http_server_requests_seconds_bucket` (P95/P99 latency).

### Custom Domain & Business KPIs
- `taskflow_login_success_total`: Total successful authentication attempts.
- `taskflow_login_failure_total`: Total failed authentication attempts (tagged by `reason`).
- `taskflow_registrations_total`: Total user registrations.
- `taskflow_organizations_created_total`: Organizations created.
- `taskflow_workspaces_created_total`: Workspaces created (tagged by `type`).
- `taskflow_projects_created_total`: Projects created (tagged by `scope`).
- `taskflow_tasks_created_total`: Tasks created (tagged by `status`, `priority`).
- `taskflow_tasks_completed_total`: Tasks completed (tagged by `priority`).
- `taskflow_file_uploads_total`: Evidence / attachment uploads (tagged by `file_type`).
- `taskflow_websocket_connections_active`: Active STOMP WebSocket gauge.

---

## 6. Alerting Rules (Alertmanager)

Alertmanager processes rules defined in `monitoring/prometheus/alerts.yml`:

| Alert Name | Condition | Severity | Description |
| :--- | :--- | :--- | :--- |
| `HighCpuUsage` | `system_cpu_usage > 0.80` for 2m | `warning` | System CPU above 80%. |
| `HighMemoryUsage` | `jvm_heap_used / max > 0.85` for 2m | `critical` | JVM Heap usage above 85%. |
| `DatabaseDown` | `hikaricp_connections == 0` for 1m | `critical` | HikariCP pool unavailable. |
| `HighHttp5xxErrorRate` | `5xx_rate / total_rate > 5%` for 2m | `critical` | HTTP 5xx error rate above 5%. |
| `HighApiLatency` | `P95_latency > 1.5s` for 3m | `warning` | P95 response latency exceeds 1.5s. |
| `HighDiskUsage` | `disk_free < 15%` for 5m | `warning` | Disk free space below 15%. |
| `DeadJvmInstance` | `up == 0` for 30s | `page` | Taskflow app instance unreachable. |
| `TooManyLoginFailures` | `rate(login_failure) > 10/min` | `warning` | Potential brute-force attack detected. |

---

## 7. Grafana Dashboards

Grafana (`http://localhost:3000`) is auto-provisioned with credentials (`admin` / `${GRAFANA_ADMIN_PASSWORD}`):

1. **Taskflow - Platform & System Health**:
   - Real-time CPU, JVM Heap, HTTP RPS, HikariCP pool, P95/P99 Latency, 4xx/5xx error rates.
2. **Taskflow - Business KPIs & Usage**:
   - Total registrations, login success vs failure rates, active WebSockets, task creation vs completion velocity, organization/project creation timelines.

---

## 8. Operational Guides

### How to Debug a Slow Request
1. Open **Grafana** → Navigate to **Loki / Explore**.
2. Filter logs for the slow request URL or user: `{service_name="taskflow"} |= "slow"`.
3. Copy the `traceId` from the JSON log line.
4. Click the **TraceID** derived link directly in Grafana to open the trace in **Tempo**.
5. View the waterfall diagram showing exact millisecond breakdown across Controller → Service → Database SQL query.

### How to Add a New Custom Metric
1. Inject `TaskMetrics` into your target service/component.
2. Call or add a meter method in `TaskMetrics.java`:
   ```java
   public void recordCustomEvent(String category) {
       registry.counter("taskflow_custom_event_total", "category", category).increment();
   }
   ```
3. Prometheus will scrape the new metric automatically at `/actuator/prometheus`.

---

## 9. Security & Sensitive Data Masking

- **Actuator Endpoint Security**: All Actuator endpoints require `ROLE_SUPER_ADMIN` except `/actuator/health` and `/actuator/prometheus` (internal docker network only).
- **Data Masking Protocol**:
  - Passwords, JWT Tokens, Refresh Tokens, Verification Tokens, Authorization Headers, and Secret keys are strictly stripped/masked prior to MDC logging.
