# Architecture Principles

Back to **[Master Index](README.md)** | View **[Architectural Constraints](architecture.md#3-architectural-constraints)**

---

These principles guide all architectural decisions in the Ryokai backend. They complement the specific [Architectural Constraints (AC-1 through AC-10)](architecture.md#3-architectural-constraints) by defining the underlying reasoning that produces those constraints.

---

## Core Principles

### P-1: Security First
Every feature is designed with security as a constraint, not a feature. Authentication and authorization are not bolt-ons — they are structural.

**In practice:**
- JWT validation occurs before any controller code executes ([ADR-002](adr/002-jwt-auth.md)).
- Permission checks happen at two levels: `@PreAuthorize` on controller methods AND strategy/validator checks in services ([AC-5](architecture.md#3-architectural-constraints)).
- Super Admins are architecturally isolated from tenant data ([AC-7](architecture.md#3-architectural-constraints)).
- Rate limiting is applied at the filter chain level, not inside controllers.
- WebSocket connections require JWT authentication on CONNECT frame.

### P-2: Explicit Over Implicit
Business rules must be visible in code, not hidden in conventions, configuration, or database triggers.

**In practice:**
- Task mode resolution is explicit via `TaskStrategyFactory`, not inferred from context ([ADR-001](adr/001-strategy-pattern.md)).
- Role priority is an explicit integer field, not derived from role name ordering.
- Cross-mode dependency prohibition is enforced by code validation, not by database constraints alone.
- Permission types are defined as an enum (`PermissionType`), not as arbitrary strings.
- Soft deletes use explicit `deleted` + `deletedAt` fields, not framework magic.

### P-3: Service Layer Owns Business Logic
Controllers handle HTTP concerns (request parsing, response formatting, status codes). Repositories handle data access. All business logic — validation, state transitions, authorization decisions, side effects — lives in the service layer.

**In practice:**
- Controllers never inject repositories ([AC-1](architecture.md#3-architectural-constraints)).
- Domain entities are pure JPA POJOs — no business methods beyond `transitionTo()` ([AC-2](architecture.md#3-architectural-constraints)).
- Strategies operate on domain objects and return booleans — they don't call controllers ([AC-3](architecture.md#3-architectural-constraints)).
- DTOs never reach the repository layer ([AC-4](architecture.md#3-architectural-constraints)).

### P-4: Stateless API Design
The backend never stores request state between API calls. Every request carries all information needed for processing.

**In practice:**
- JWT tokens carry user identity, roles, and token version — no server-side sessions ([ADR-002](adr/002-jwt-auth.md)).
- CSRF protection is disabled because stateless APIs with Bearer tokens are not vulnerable to CSRF.
- Session creation policy is `STATELESS` in Spring Security configuration.
- Horizontal scaling requires no session affinity or sticky load balancing.

### P-5: Isolation by Default
Data visibility defaults to private. Sharing is an explicit, auditable action.

**In practice:**
- Personal workspace data is invisible to everyone except the creator ([ADR-003](adr/003-tri-modal-workspaces.md)).
- Organization data forms a sealed vault — no external visibility.
- Project sharing with crews is an explicit action via bridge connection, not automatic.
- Enterprise projects are blocked from external sharing ([AC-9](architecture.md#3-architectural-constraints)).

### P-6: Event-Driven Where Appropriate
Side effects (notifications, audit, email) are triggered by domain events, not embedded inline in business methods.

**In practice:**
- `TaskStatusChangedEvent` triggers notification creation, audit logging, and WebSocket pushes.
- `EvidenceUploadedEvent` triggers reviewer notifications.
- `NotificationCreatedEvent` triggers WebSocket delivery to connected clients.
- Async processing via dedicated thread pools (`emailExecutor`, `realtimeExecutor`, `auditExecutor`) prevents side effects from blocking request threads.

### P-7: Fail Loudly, Recover Gracefully
Errors should be visible and structured, not silently swallowed. But error handling should not crash the system.

**In practice:**
- `GlobalExceptionHandler` maps all 16 exception types to structured JSON responses with correlation IDs.
- `CallerRunsPolicy` on async executors provides backpressure without dropping work.
- Optimistic locking conflicts return HTTP 409 with a clear error code — not a 500.
- Rate limit exceeded returns HTTP 429 with retry-after guidance.

### P-8: Audit Everything That Matters
Security-sensitive operations, state transitions, and data mutations are recorded in immutable audit tables.

**In practice:**
- `TaskStatusHistory` records every status transition with actor, timestamp, and before/after states.
- `SecurityAuditEvent` records authentication events (login, logout, password change, failed attempts).
- `TaskActivityLog` and `ProjectActivityLog` capture CRUD operations with correlation IDs and IP addresses.
- `AuditEvent` captures entity-level changes with old/new JSONB values.
- MDC correlation IDs propagate across async boundaries to connect audit entries ([AC-10](architecture.md#3-architectural-constraints)).

### P-9: Design for the Next Developer
Code should be understandable by a developer who has never seen it before. Documentation, naming, and structure should reduce onboarding time.

**In practice:**
- ADRs explain *why* decisions were made, not just *what* was built.
- Architectural constraints are explicit numbered rules, not tribal knowledge.
- Exception types have descriptive names (`InvalidStateTransitionException`, not `BadRequestException`).
- Strategy pattern makes task mode behavior discoverable without reading conditional chains.
- Verification levels (✅ Verified, 🔍 Observed, ⚠️ Needs Verification) in documentation distinguish facts from assumptions.

---

## Principle Application Guide

When evaluating a new feature or architectural change, check it against these principles in order:

1. **Does it maintain security boundaries?** (P-1)
2. **Is the behavior explicit in code?** (P-2)
3. **Does business logic live in the service layer?** (P-3)
4. **Does it preserve stateless API design?** (P-4)
5. **Does it default to private/isolated?** (P-5)
6. **Are side effects event-driven?** (P-6)
7. **Are errors structured and visible?** (P-7)
8. **Are important operations audited?** (P-8)
9. **Will the next developer understand it?** (P-9)

If a proposed change violates a principle, document the trade-off in an ADR.
