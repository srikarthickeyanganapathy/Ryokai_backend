# Ryokai Backend Engineering Manual

```
===================================================================================
 SYSTEM SPECIFICATION & DOCUMENT METADATA
===================================================================================
 Document Version   : 1.4.0-PROD-SPEC
 Target Framework   : Spring Boot 3.2.x / Spring Security 6.x
 JDK Version        : Java 17 LTS
 Persistence Layer  : Spring Data JPA / Hibernate 6 (PostgreSQL 15+)
 Transport Protocols: Synchronous REST (HTTP/1.1) + WebSocket (STOMP, native)
 Migration Engine   : Flyway (46 versioned migrations: V1 – V46)
 Notice             : Derived from current backend implementation. Update 
                      alongside significant backend architectural changes.
===================================================================================
```

---

## Technology Compatibility Matrix

| Component | Technology | Version | Scope / Notes |
| :--- | :--- | :--- | :--- |
| **Language** | Java | 17 LTS | Core Runtime |
| **Framework** | Spring Boot | 3.2.x | Application Framework |
| **Security** | Spring Security | 6.x | Authentication & Authorization |
| **ORM / JPA** | Hibernate / Spring Data JPA | 6.x | Relational Persistence |
| **Database** | PostgreSQL | 15+ | Production Data Store (JSONB for audit metadata) |
| **Migration** | Flyway | 10.x | 46 versioned migration scripts |
| **WebSocket** | Spring STOMP (native) | 3.2.x | Real-time task updates, whiteboard, notifications |
| **Rate Limiting** | Bucket4j + Caffeine | 8.x | In-Memory Token Bucket (IP-based + per-user) |
| **Async** | Spring @Async | 3.2.x | Email, realtime, audit thread pools with MDC propagation |
| **JWT** | JJWT | 0.12.6 | HS256 dual-key (access + refresh), token versioning |
| **Email** | Spring Mail | 3.2.x | Gmail SMTP (async via `emailExecutor`) |
| **Monitoring** | Spring Boot Actuator | 3.2.x | Health + Prometheus endpoints |
| **Documentation** | OpenAPI / Swagger | 3.0 (Springdoc) | Interactive API Spec |
| **Build** | Maven | 3.9.x | Single-module build |

---

## Navigation Portal

Welcome to the Ryokai Backend Engineering Manual. The technical specification is organized into modular documents:

- 🏗️ **[Architecture Overview](architecture.md)** — Deployment topology, package layer dependencies, and architectural constraints.
- 📜 **[Architecture Decision Records (ADRs)](adr/README.md)** — Individual ADRs detailing architectural design decisions.
- 🧬 **[Domain Model & Entity Catalogue](domain.md)** — Tri-modal workspace rules, entity relationship diagrams, and entity blueprints.
- 🛡️ **[Security Architecture](security.md)** — Spring Security 6 filter chain, custom SpEL evaluators, RBAC role-priority matrix, rate limiting, and security audit.
- 🔌 **[API Reference Catalogue](api.md)** — Complete endpoint inventory (35 controllers), HTTP verbs, permissions, DTO specifications, and OpenAPI links.
- 🔄 **[Workflows & Sequence Diagrams](workflows.md)** — Comprehensive workflow catalog, entity state machines, and sequence diagrams.
- ⚡ **[Async Threading & Notification System](async-and-notifications.md)** — Thread pool executors, MDC propagation, notification pipeline (DB + WebSocket + Email), and deduplication.
- 🛠️ **[Operations & Operational Runbooks](operations.md)** — Configuration reference, MDC trace logging, operational runbooks, secret rotation, known issues, and limits.
- 🧪 **[Developer Guide & Onboarding](developer-guide.md)** — Step-by-step feature implementation walkthrough, testing infrastructure guide, exception catalogue, and domain glossary.
- 🚀 **[Future Architecture & Evolution Roadmap](future-architecture.md)** — Event bus abstraction, outbox pattern, search, object storage, Redis, aggregate boundaries, CQRS, API versioning, feature flags, and background jobs.

---

## Documentation Revision History

| Revision | Date | Commit Reference | Core Architectural Changes |
| :--- | :--- | :--- | :--- |
| **v1.0.0** | 2026-07-20 | `e8a91b2` | Core Task engine, Personal Workspace, Basic Authentication |
| **v1.1.0** | 2026-07-21 | `f3c72d1` | Crew Collaboration Workspace, STOMP Whiteboards, Invite Link system |
| **v1.2.0** | 2026-07-21 | `a918f4e` | Organization Multi-Tenancy, Role Priority Governance, HR Leave pipeline |
| **v1.3.0** | 2026-07-22 | `7d21a0f` | Catalogued all system workflows across Auth, Org, Crew, Task, and Bridge |
| **v1.4.0** | 2026-07-23 | `—` | Full architecture audit: async threading, notification pipeline, CorrelationIdFilter verified, 35 controllers inventoried, domain events, announcement/team-chat/evidence/workload APIs documented, architectural constraints added |

---

## Sequence Diagram Index

1. **[Diagram 1: User Registration & Authentication Flow](workflows.md#diagram-1-user-registration--authentication-flow)**
2. **[Diagram 2: JWT Refresh Token Rotation](workflows.md#diagram-2-jwt-refresh-token-rotation)**
3. **[Diagram 3: Enterprise Task Assignment & Hierarchy Validation](workflows.md#diagram-3-enterprise-task-assignment--hierarchy-validation)**
4. **[Diagram 4: Task Evidence Upload & Submission](workflows.md#diagram-4-task-evidence-upload--submission)**
5. **[Diagram 5: Manager Task Approval Flow](workflows.md#diagram-5-manager-task-approval-flow)**
6. **[Diagram 6: Manager Task Rejection Flow](workflows.md#diagram-6-manager-task-rejection-flow)**
7. **[Diagram 7: Assignee Task Recall Sequence](workflows.md#diagram-7-assignee-task-recall-sequence)**
8. **[Diagram 8: Task Reassignment Flow](workflows.md#diagram-8-task-reassignment-flow)**
9. **[Diagram 9: Crew Task Claiming Flow](workflows.md#diagram-9-crew-task-claiming-flow)**
10. **[Diagram 10: Convert Chat Message to Task Flow](workflows.md#diagram-10-convert-chat-message-to-task-flow)**
11. **[Diagram 11: Crew Real-Time Whiteboard Drawing Flow](workflows.md#diagram-11-crew-real-time-whiteboard-drawing-flow)**
12. **[Diagram 12: Project Connection Bridge (Sharing & Revocation)](workflows.md#diagram-12-project-connection-bridge-sharing--revocation)**
13. **[Diagram 13: HR Leave Request & Task Reassignment](workflows.md#diagram-13-hr-leave-request--task-reassignment)**
14. **[Diagram 14: Trace ID MDC Logging Lifecycle](operations.md#2-diagram-10-trace-id-mdc-logging-lifecycle)**
