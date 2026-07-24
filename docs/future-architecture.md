# Architecture Evolution Roadmap

Back to **[Master Index](README.md)** | View **[Architecture Overview](architecture.md)**

---

This document tracks the technical evolution of the Ryokai Taskflow backend across past releases, active efforts, and future architectural milestones. 

### Roadmap Item Lifecycle

| Status Icon | Lifecycle Phase | Definition |
| :--- | :--- | :--- |
| **✅ Completed (vX.Y.Z)** | Delivered | Implemented in code, verified with tests, and retained as a permanent historical record. |
| **🚧 In Progress** | Active Work | Implementation actively underway in current development phase. |
| **📅 Planned (Near-Term)** | Approved | Architecture approved for immediate execution (0–6 months). |
| **💡 Strategic / Future** | Long-Term | Evaluated concepts and scaling milestones (6–18 months). |
| **❌ Dropped / Superseded** | Archived | Architecture decisions that were superseded or deemed unnecessary, with rationale documented. |

---

## 1. ✅ Completed (v1.5.0)

### 1.1 Domain Event Publisher Abstraction (`DomainEventPublisher`)

**Status**: ✅ Completed (v1.5.0) | **Impact**: High

- **Implemented In**:
  - [`event/DomainEventPublisher.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/event/DomainEventPublisher.java)
  - [`event/SpringDomainEventPublisher.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/event/SpringDomainEventPublisher.java)
- **Services Updated**: `NotificationService`, `TaskStateTransitionServiceImpl`, `TaskEvidenceService`.
- **Notes**: Originally identified in v1.4.0 audit to decouple domain services from Spring's `ApplicationEventPublisher`. Serves as the foundation for event broker migrations (Kafka, RabbitMQ, Outbox).

---

### 1.2 Transactional Outbox Pattern

**Status**: ✅ Completed (v1.5.0) | **Impact**: Critical

- **Implemented In**:
  - `db/migration/V47__create_outbox_events_table.sql`
  - [`domain/OutboxEvent.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/domain/OutboxEvent.java)
  - [`repository/OutboxEventRepository.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/repository/OutboxEventRepository.java)
  - [`event/OutboxDomainEventPublisher.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/event/OutboxDomainEventPublisher.java)
  - [`event/OutboxPoller.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/event/OutboxPoller.java)
- **Notes**: Guarantees zero event loss by storing events atomically in `outbox_events` within the caller's DB transaction. Toggleable via `app.events.publisher=outbox`.

---

### 1.3 Universal API Versioning (`/api/v1/*`)

**Status**: ✅ Completed (v1.5.0) | **Impact**: High

- **Implemented In**: All 35 REST Controllers and `SecurityConfig.java`.
- **Notes**: Prefixed all public/authenticated REST endpoints under `/api/v1/...` namespace to protect client compatibility ahead of frontend release.

---

### 1.4 Full-Stack Observability & Distributed Tracing Stack

**Status**: ✅ Completed (v1.5.0) | **Impact**: Critical

- **Implemented In**:
  - `pom.xml` (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`)
  - `application.yml` & `logback-spring.xml` (MDC correlation enrichment: `correlationId`, `traceId`, `spanId`, `userId`, `requestId`)
  - `monitoring/` stack (`tempo`, `loki`, `alloy`, `prometheus`, `alertmanager`, `grafana`)
  - `docs/observability.md`
- **Notes**: Full 3D Observability stack with auto-instrumentation, derived trace-log links in Grafana, custom business KPI metrics, and 8 Alertmanager alert rules.

---

### 1.5 Client IP Security & Trusted Proxy Resolution (`ClientIpResolver`)

**Status**: ✅ Completed (v1.5.0) | **Impact**: High

- **Implemented In**: [`security/ClientIpResolver.java`](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/security/ClientIpResolver.java). Integrated into `RateLimitFilter`, `AuthController`, and `SessionController`.
- **Notes**: Resolves `SEC-11` open audit item by enforcing configurable trusted proxy IP whitelist (`app.security.trusted-proxies`) when parsing `X-Forwarded-For`.

---

### 1.6 Externalized CORS Configuration

**Status**: ✅ Completed (v1.5.0) | **Impact**: Medium

- **Implemented In**: `application.yml` (`app.security.cors.allowed-origins`), `SecurityConfig.java`, `WebSocketConfig.java`.
- **Notes**: Resolves `SEC-12` open audit item by externalizing allowed origin URLs for easy environment deployment.

---

## 2. 🚧 In Progress

*No active in-progress items at this timestamp. All v1.5.0 roadmap items are fully implemented and verified.*

---

## 3. 📅 Planned (Near-Term: 0–6 Months)

### 3.1 Distributed Cache Migration (Redis)

**Status**: 📅 Planned | **Priority**: Immediate (Before Multi-Node Deployment) | **Effort**: Low

- **Target State**: Replace single-node Caffeine caches with Redis for:
  1. `TokenDenylistService` (revoked JWT tokens)
  2. `RateLimitFilter` & `AuthController` (Bucket4j distributed rate-limit buckets)
- **Rationale**: #1 prerequisite for scaling backend horizontally behind a load balancer.

---

### 3.2 Background Job Scheduler (ShedLock + `@Scheduled`)

**Status**: 📅 Planned | **Priority**: High | **Effort**: Low

- **Target State**: Integrate ShedLock over Spring `@Scheduled` to execute distributed cron jobs:
  - Daily cleanup of expired `OrganizationInvite` and `CrewInvite` records.
  - Daily purge of expired `RefreshToken` and `PasswordResetToken` entries.
  - Weekly notification archival.
  - Periodic task due-date reminder triggers.

---

### 3.3 Direct S3 Object Storage for Task Evidence

**Status**: 📅 Planned | **Priority**: High | **Effort**: Medium

- **Target State**: Introduce S3-compatible pre-signed upload/download URLs (AWS S3, MinIO, Cloudflare R2).
- **Rationale**: Offloads file payload bytes from Spring Boot JVM memory directly to object storage.

---

### 3.4 Explicit Domain Aggregate Boundaries Documentation

**Status**: 📅 Planned | **Priority**: High | **Effort**: Documentation Only

- **Target State**: Document strict transactional boundary invariants for Task Aggregate, Project Aggregate, Organization Aggregate, Crew Aggregate, and User Aggregate to prevent unintended cross-aggregate mutations in future features.

---

### 3.5 Unified Activity Log Schema Evaluation

**Status**: 📅 Planned | **Priority**: Medium | **Effort**: Medium

- **Target State**: Evaluate consolidating `task_activity_logs` and `project_activity_logs` into a single partitioned `activity_logs` table (`entity_type`, `entity_id`, `action_type`, `metadata_json`).

---

## 4. 💡 Strategic / Future (6–18 Months)

### 4.1 Full-Text Search Infrastructure (PostgreSQL `tsvector` / Meilisearch)

**Status**: 💡 Strategic | **Priority**: Medium | **Effort**: Medium

- **Trigger**: When organization task count > 100,000 or cross-entity search (tasks + comments + notes) is requested.

---

### 4.2 CQRS Read Model for High-Volume Analytics

**Status**: 💡 Strategic | **Priority**: Medium | **Effort**: High

- **Trigger**: When dashboard aggregate analytics endpoints (`GET /api/v1/dashboard/stats`) exceed 100ms P95 latency.

---

### 4.3 Enterprise Feature Flag System

**Status**: 💡 Strategic | **Priority**: Medium | **Effort**: Medium

- **Target State**: Database-backed feature flags with per-organization targeting for zero-downtime feature rollouts.

---

## 5. ❌ Dropped / Superseded

*No items dropped or superseded in v1.5.0.*

---

## Evolution Matrix & Summary

```mermaid
quadrantChart
    title Architecture Evolution Priority Matrix
    x-axis Low Effort --> High Effort
    y-axis Low Impact --> High Impact
    quadrant-1 Do First
    quadrant-2 Plan Carefully
    quadrant-3 Quick Wins
    quadrant-4 Defer
    Redis Cache: [0.25, 0.95]
    API Versioning (Done): [0.15, 0.35]
    Event Bus Interface (Done): [0.2, 0.7]
    Outbox Pattern (Done): [0.5, 0.9]
    Observability Stack (Done): [0.45, 0.95]
    Client IP Security (Done): [0.15, 0.85]
    CORS Externalization (Done): [0.1, 0.5]
    Background Jobs: [0.3, 0.75]
    Object Storage: [0.35, 0.8]
    Aggregate Docs: [0.1, 0.6]
    Feature Flags: [0.55, 0.5]
    Unified Activity: [0.45, 0.55]
    Full-Text Search: [0.65, 0.55]
    CQRS Read Model: [0.85, 0.6]
```

| Lifecycle Phase | Count | Key Milestones |
| :--- | :--- | :--- |
| **✅ Completed (v1.5.0)** | 6 | Event Bus Abstraction, Outbox Pattern, Universal API Versioning (`/api/v1`), 3D Observability Stack, Client IP Whitelist, CORS Externalization |
| **🚧 In Progress** | 0 | All v1.5.0 targets delivered |
| **📅 Planned (Near-Term)** | 5 | Redis Cache, ShedLock Jobs, S3 Direct Storage, Aggregate Boundaries, Unified Activity Log |
| **💡 Strategic / Future** | 3 | Full-Text Search, CQRS Read Models, Feature Flags |
