# Ryokai Backend Engineering Manual

```
===================================================================================
 SYSTEM SPECIFICATION & DOCUMENT METADATA
===================================================================================
 Document Version   : 1.0.0-PROD-SPEC
 Target Framework   : Spring Boot 3.2.x / Spring Security 6.x
 JDK Version        : Java 17 LTS
 Persistence Layer  : Spring Data JPA / Hibernate (MySQL 8.0 / PostgreSQL 15 / H2)
 Transport Protocols: Synchronous REST (HTTP/1.1) + WebSocket (STOMP over SockJS)
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
| **Database** | MySQL / PostgreSQL / H2 | 8.0+ / 15+ | Relational Data Store |
| **WebSocket** | Spring STOMP / SockJS | 3.2.x | Real-time Canvas Broadcast |
| **Rate Limiting** | Bucket4j | 8.x | In-Memory Token Bucket |
| **Documentation** | OpenAPI / Swagger | 3.0 (Springdoc) | Interactive API Spec |

---

## Navigation Portal

Welcome to the Ryokai Backend Engineering Manual. The technical specification is organized into modular documents:

- 🏗️ **[Architecture Overview](architecture.md)** — Deployment topology and package layer dependencies.
- 📜 **[Architecture Decision Records (ADRs)](adr/README.md)** — Individual ADRs detailing architectural design decisions.
- 🧬 **[Domain Model & Entity Catalogue](domain.md)** — Tri-modal workspace rules, entity relationship diagrams, and entity blueprints.
- 🛡️ **[Security Architecture](security.md)** — Spring Security 6 filter chain, custom SpEL evaluators, RBAC role-priority matrix, and security audit.
- 🔌 **[API Reference Catalogue](api.md)** — Endpoint mappings, HTTP verbs, permissions, DTO specifications, and OpenAPI links.
- 🔄 **[Workflows & Sequence Diagrams](workflows.md)** — Sequence diagrams, entity state machines, and controller call chains.
- 🛠️ **[Operations & Operational Runbooks](operations.md)** — Configuration reference, MDC trace logging, operational runbooks, secret rotation, known issues, and limits.
- 🧪 **[Developer Guide & Onboarding](developer-guide.md)** — Step-by-step feature implementation walkthrough, testing infrastructure guide, exception catalogue, and domain glossary.

---

## Documentation Revision History

| Revision | Date | Commit Reference | Core Architectural Changes |
| :--- | :--- | :--- | :--- |
| **v1.0.0** | 2026-07-20 | `e8a91b2` | Core Task engine, Personal Workspace, Basic Authentication |
| **v1.1.0** | 2026-07-21 | `f3c72d1` | Crew Collaboration Workspace, STOMP Whiteboards, Invite Link system |
| **v1.2.0** | 2026-07-21 | `a918f4e` | Organization Multi-Tenancy, Role Priority Governance, HR Leave pipeline |
| **v1.3.0** | 2026-07-22 | `7d21a0f` | Verified REST controllers, STOMP drawing interceptors, Goal/OKR org scoping |

---

## Sequence Diagram Index

1. **[Diagram 1: User Registration & Authentication Flow](workflows.md#diagram-1-user-registration--authentication-flow)**
2. **[Diagram 2: JWT Refresh Token Rotation](workflows.md#diagram-2-jwt-refresh-token-rotation)**
3. **[Diagram 3: Enterprise Task Assignment & Hierarchy Validation](workflows.md#diagram-3-enterprise-task-assignment--hierarchy-validation)**
4. **[Diagram 4: Task Evidence Upload & Submission](workflows.md#diagram-4-task-evidence-upload--submission)**
5. **[Diagram 5: Manager Approval & Rejection Flow](workflows.md#diagram-5-manager-approval--rejection-flow)**
6. **[Diagram 6: Assignee Task Recall Sequence](workflows.md#diagram-6-assignee-task-recall-sequence)**
7. **[Diagram 7: Crew Real-Time Whiteboard Drawing Flow](workflows.md#diagram-7-crew-real-time-whiteboard-drawing-flow)**
8. **[Diagram 8: Project Connection Bridge (Sharing & Revocation)](workflows.md#diagram-8-project-connection-bridge-sharing--revocation)**
9. **[Diagram 9: HR Leave Request & Task Reassignment](workflows.md#diagram-9-hr-leave-request--task-reassignment)**
10. **[Diagram 10: Trace ID MDC Logging Lifecycle](operations.md#diagram-10-trace-id-mdc-logging-lifecycle)**
