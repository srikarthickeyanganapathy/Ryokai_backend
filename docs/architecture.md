# System Architecture Overview

Back to **[Master Index](README.md)** | View **[Architecture Decision Records](adr/README.md)**

---

## 1. Deployment Topology

```mermaid
graph TD
    Client["Frontend SPA (React / Vite)<br/>Port: 5173 / Netlify"] -->|REST / HTTP 1.1| AppServer
    Client -->|WebSocket / STOMP (native)| AppServer
    
    subgraph AppServer["Spring Boot 3.2 Backend Container (Port 8080)"]
        direction TB
        CorrelationFilter["1. CorrelationIdFilter<br/>(MDC: X-Correlation-Id, Order: HIGHEST_PRECEDENCE)"]
        CorsFilter["2. CorsFilter<br/>(Origins: localhost:5173, Netlify, DevTunnels)"]
        RateLimiter["3. RateLimitFilter<br/>(Bucket4j + Caffeine, /api/auth/* only)"]
        JwtAuth["4. JwtAuthenticationFilter<br/>(Bearer JWT → SecurityContext + token version check)"]
        UserAuthFilter["5. UsernamePasswordAuthenticationFilter<br/>(Bypassed for JWT; active for /api/auth/login)"]
        ExceptionFilter["6. ExceptionTranslationFilter<br/>(Catches AccessDenied/AuthenticationException)"]
        SecurityInterceptor["7. MethodSecurity<br/>(SpEL: CustomPermissionEvaluator → DomainPermissionHandlers)"]
        
        CorrelationFilter --> CorsFilter
        CorsFilter --> RateLimiter
        RateLimiter --> JwtAuth
        JwtAuth --> UserAuthFilter
        UserAuthFilter --> ExceptionFilter
        ExceptionFilter --> SecurityInterceptor
        SecurityInterceptor --> Dispatcher["DispatcherServlet"]
        
        Dispatcher --> RESTControllers["REST Controllers (35 classes)"]
        Dispatcher --> WSController["WebSocket Controllers (STOMP)"]
        
        RESTControllers --> Services["Domain Services & Strategies"]
        WSController --> Broadcaster["RealtimeBroadcaster"]
    end
    
    subgraph AsyncPools["Async Thread Pools (AsyncConfig)"]
        EmailExec["emailExecutor<br/>core=2, max=5, queue=1000"]
        RealtimeExec["realtimeExecutor<br/>core=2, max=4, queue=500"]
        AuditExec["auditExecutor<br/>core=2, max=4, queue=1000"]
    end
    
    Services --> AsyncPools
    
    subgraph DataStorage["Persistence & External Services"]
        RDBMS[("PostgreSQL 15+<br/>Flyway: 46 migrations<br/>JSONB audit columns")]
        SMTP["Gmail SMTP<br/>(Async via emailExecutor)"]
    end
    
    Services -->|JDBC / HikariCP| RDBMS
    AsyncPools -->|JavaMailSender| SMTP
    
    subgraph FutureState["Future Infrastructure (Planned)"]
        RedisCache["Redis (Distributed Denylist / Rate Limit)"]
        KafkaBroker["Kafka / RabbitMQ (Async Event Bus)"]
        S3Storage["AWS S3 / MinIO (Object Storage for Evidence)"]
        OpenTelemetry["OpenTelemetry Collector + Grafana"]
    end

    Services -.->|Planned| RedisCache
    Services -.->|Planned| KafkaBroker
    Services -.->|Planned| S3Storage
    AppServer -.->|Planned| OpenTelemetry
```

---

## 2. Package Layer Dependencies & Boundaries

```
src/main/java/com/example/taskflow/
├── config/              # Global Configuration & Security Chain
│   ├── SecurityConfig           # Filter chain, CORS, HTTP headers, BCrypt(12)
│   ├── WebSocketConfig          # STOMP broker, heartbeats, transport limits
│   ├── AsyncConfig              # 3 thread pools + MDC decorator
│   ├── CorrelationIdFilter      # MDC trace ID (X-Correlation-Id)
│   ├── GlobalExceptionHandler   # @RestControllerAdvice (custom & framework exception handlers)
│   ├── DataSeeder               # Permission bootstrap on startup
│   ├── MethodSecurityConfig     # @EnableMethodSecurity + CustomPermissionEvaluator
│   ├── OpenApiConfig            # Springdoc OpenAPI 3.0 configuration
│   ├── JacksonConfig            # Jackson ObjectMapper customization
│   └── WebSocketHandshakeInterceptor  # Origin validation on WS upgrade
├── controller/          # REST Controllers
├── domain/              # JPA Entities & Enums
│   └── events/task/     # Spring ApplicationEvents (TaskStatusChangedEvent, EvidenceUploadedEvent)
├── dto/                 # Request & Response DTO Data Contracts
├── exception/           # Custom Domain Runtime Exceptions
├── mapper/              # DTO ↔ Entity mappers (TaskResponseMapper, etc.)
├── notification/        # Notification event types, email renderers, WebSocket listener
├── repository/          # Spring Data JPA Repositories
├── security/            # SpEL Evaluators, Permission Handlers, Role Strategies, Rate Limiting
├── service/             # Domain Services & Business Logic
│   └── impl/            # Service implementations & TaskActivityEventListener (@TransactionalEventListener)
├── strategy/task/       # Task Scope Lifecycle Strategies (Strategy Pattern)
│   ├── TaskLifecycleStrategy    # Base interface (10 methods)
│   ├── TaskScopeBehavior        # Mixin: initialStatus, canBeReviewed, onComplete
│   ├── Approvable               # Mixin: canSubmit, canApprove, canReject (Org only)
│   ├── Claimable                # Mixin: canClaim (Crew only)
│   ├── PersonalTaskStrategy     # PERSONAL mode implementation
│   ├── CrewTaskStrategy         # CREW mode implementation
│   ├── OrgTaskStrategy          # ORG mode implementation
│   └── TaskStrategyFactory      # Mode → Strategy resolver
└── util/                # JWT utilities, authentication filter, TaskMetrics
```

---

## 3. Architectural Constraints

These are the rules that govern the codebase structure and must be maintained as the system grows. They are derived from the **[Architecture Principles (P-1 through P-9)](architecture-principles.md)**:

| # | Constraint | Rationale |
| :--- | :--- | :--- |
| AC-1 | **Controllers never inject Repositories directly.** All database access goes through Service layer. | Ensures business logic is centralized and testable. |
| AC-2 | **Domain Entities are pure JPA POJOs.** No `@Autowired`, no business methods beyond `transitionTo()`. | Prevents hidden coupling and keeps entities portable. |
| AC-3 | **Strategies never reference Controller classes.** They operate on domain objects and return booleans. | Maintains clean layered separation. |
| AC-4 | **DTOs never reach Repository layer.** Services map DTOs to entities before persistence. | Prevents API contract changes from breaking queries. |
| AC-5 | **Permission checks always occur before state transitions.** Controller layer enforces baseline Authentication / Coarse Auth; Service layer enforces Business Auth. | Defense-in-depth: multi-layer authorization ([ADR-008](adr/008-hybrid-authorization-model.md)). |
| AC-6 | **Cross-mode dependencies are forbidden.** Personal tasks depend only on personal tasks (same creator); Org on Org (same org); Crew on Crew (same crew). | Enforces tri-modal workspace isolation ([ADR-003](adr/003-tri-modal-workspaces.md)). |
| AC-7 | **Super Admin cannot access organization task data.** `SuperAdminStrategy` restricts to personal tasks only. | Privacy boundary — platform operators vs. tenant data. |
| AC-8 | **Reviewers must have strictly higher role priority than assignees.** Assignees cannot self-review. | Prevents vertical privilege escalation ([ADR-005](adr/005-rbac-role-priority.md)). |
| AC-9 | **Enterprise projects (project.organization ≠ null) cannot be shared with Crews.** | Sealed corporate vault boundary. |
| AC-10 | **All async tasks propagate MDC context.** `MdcTaskDecorator` wraps every thread pool executor. | Correlation IDs survive async boundaries for end-to-end tracing. |

---

## 4. Configuration Classes Reference

| Class | Responsibility |
| :--- | :--- |
| `SecurityConfig` | Filter chain ordering, CORS, HTTP security headers (HSTS, X-Frame-Options, X-XSS-Protection), CSRF disabled, BCrypt(12), session STATELESS |
| `WebSocketConfig` | STOMP endpoints (`/ws`), simple broker (`/topic`, `/queue`), heartbeat 10s/10s, 64KB message limit, `StompAuthChannelInterceptor` |
| `AsyncConfig` | Three `ThreadPoolTaskExecutor` beans: `emailExecutor`, `realtimeExecutor`, `auditExecutor` — all with `CallerRunsPolicy` backpressure and `MdcTaskDecorator` |
| `CorrelationIdFilter` | `@Order(HIGHEST_PRECEDENCE)` — reads `X-Correlation-Id` header (validated regex `^[A-Za-z0-9-]{1,64}$`), generates UUID if missing, sets MDC and response header |
| `GlobalExceptionHandler` | `@RestControllerAdvice` — maps application-specific and framework exceptions to structured JSON `{ timestamp, status, error, message, code, path, correlationId }` |
| `DataSeeder` | `CommandLineRunner` — seeds all `PermissionType` enum values into `permissions` table (idempotent) |
| `MethodSecurityConfig` | `@EnableMethodSecurity` — registers `CustomPermissionEvaluator` as the global `PermissionEvaluator` |
| `OpenApiConfig` | Springdoc configuration for Swagger UI at `/swagger-ui/index.html` |
| `JacksonConfig` | Custom `ObjectMapper` configuration |
| `WebSocketHandshakeInterceptor` | Validates origin header during WebSocket upgrade handshake |

---

## 5. Domain Aggregate Boundaries

Five aggregate roots are defined, each with explicit transactional boundaries. See **[ADR-006](adr/006-aggregate-boundaries.md)** for the full decision record and **[Future Architecture](future-architecture.md#6-domain-aggregate-boundaries)** for the complete aggregate diagrams.

| Aggregate Root | Child Entities | Cross-Aggregate References |
| :--- | :--- | :--- |
| **Task** | ChecklistItem, TaskEvidence, TaskComment, TaskDependency, TaskStatusHistory | `task.project_id`, `task.org_id`, `task.crew_id` |
| **Project** | Collaborators (M:N), SharedCrews (M:N) | `project.owner_id` |
| **Organization** | Role, Permission, Team, TeamMember, TeamObserver, Membership, Announcement, Goal, KeyResult, LeaveRequest | — |
| **Crew** | CrewMember, CrewChannel, CrewMessage, CrewInvite, Whiteboard | `crew.creator_id` |
| **User** | RefreshToken, Note, FocusSession, CalendarEvent, SavedItem, Notification | — |

**Key rule**: Cross-aggregate references are by FK ID only. Deleting a Project soft-detaches tasks (`task.project_id = null`), never cascade-deletes them.
