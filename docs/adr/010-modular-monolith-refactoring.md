# ADR-010: Modular Monolith Refactoring

| Field | Value |
| :--- | :--- |
| **Status** | ✅ Accepted |
| **Date** | 2026-07-28 |
| **Driver** | Codebase scalability, developer onboarding, bounded context enforcement |
| **Supersedes** | Flat technical-layer architecture (controller/service/repository/domain/dto) |

---

## Context

The backend grew to 377 Java files spanning 19 business domains. The original flat, technical-layer structure (`controller/`, `service/`, `repository/`, `domain/`, `dto/`) created several problems:

1. **No locality**: A developer working on "Tasks" had to navigate across 6+ top-level directories to find all task-related code.
2. **Implicit coupling**: Any service could import any repository from any domain — no boundary enforcement.
3. **Onboarding friction**: New developers couldn't determine module ownership, dependency direction, or public vs. internal APIs.
4. **Scaling ceiling**: Adding new features (e.g., calendar, goals, focus sessions) resulted in larger flat directories with no structural guidance.

---

## Decision

Refactor the codebase into a **modular monolith** organized by bounded context (feature module), not by technical layer.

### Key Design Decisions

1. **Variable module complexity (Tier system)**. Not every module warrants the same internal structure:
   - **Tier 1** (complex): Full hexagonal architecture (`api/`, `application/`, `domain/`, `infrastructure/`). Used by: `task`, `organization`, `security`, `identity`.
   - **Tier 2** (medium): Layered with `api/`, `application/`, `domain/`, `dto/`. Used by: `crew`, `team`, `project`, `notification`, `user`, `dashboard`, `goal`.
   - **Tier 3** (simple): Flat — all files in one package. Used by: `note`, `focus`, `calendar`, `whiteboard`, `saveditem`.

2. **Identity separated from Security**. Authentication is a business capability (registration, login, password reset) — it lives in `identity/`. Security is infrastructure (JWT, filters, authorization pipeline) — it lives in `security/`.

3. **Organization split into sub-domains**. The organization module was too large as a single module. Split into: `core/` (entity, lifecycle), `membership/` (invites, leaves, members), `rbac/` (roles, permissions, scopes), `announcement/`.

4. **Domain events are internal mechanisms**. They live in `shared/events/`, not in `integration/`. External adapters (email, websocket) have their own `integration/` module.

5. **TaskStrategyFactory is orchestration, not infrastructure**. It selects a strategy based on task mode — no external I/O involved. Lives in `task/application/strategy/`, not `task/infrastructure/`.

6. **Shared kernel kept minimal**. Only `shared/events/`, `shared/exception/`, and `shared/util/` — types genuinely needed by multiple modules. Resists becoming a dumping ground.

---

## Module Inventory (21 modules)

| Module | Tier | Files | Responsibility |
| :--- | :--- | :--- | :--- |
| `app` | Infra | 5 | Global configs, exception handler |
| `audit` | 2 | 5 | Audit event recording |
| `bootstrap` | Infra | 1 | Startup data seeding |
| `calendar` | 3 | 5 | Calendar events |
| `crew` | 2 | 28 | Crew management, channels, messaging |
| `dashboard` | 2 | 9 | Read-only dashboard orchestration |
| `focus` | 3 | 5 | Focus/deep-work sessions |
| `goal` | 2 | 7 | Goals & OKRs |
| `identity` | 1 | 24 | Authentication, sessions, tokens |
| `integration` | Infra | 5 | Email, WebSocket adapters |
| `note` | 3 | 5 | Notes |
| `notification` | 2 | 13 | Notification dispatch & rendering |
| `organization` | 1 | 67 | Multi-tenant org (core, membership, rbac, announcement) |
| `platform` | 2 | 7 | Super-admin operations |
| `project` | 2 | 13 | Project management |
| `saveditem` | 3 | 6 | Bookmarks |
| `security` | 1 | 37 | JWT, authorization, filters, permissions |
| `shared` | Kernel | 7 | Domain events, shared exceptions, utilities |
| `task` | 1 | 82 | Task management (richest domain) |
| `team` | 2 | 21 | Team management |
| `user` | 2 | 11 | User profiles |
| `whiteboard` | 3 | 6 | Collaborative whiteboards |

---

## Cross-Module Communication Rules

1. **Modules communicate through application services (public API), not by importing each other's repositories.**
2. **Domain entities may be referenced cross-module** (e.g., `Task` references `User`, `Organization`) — but mutations go through the owning module's service.
3. **No cyclic module dependencies** at the application service level.
4. **Infrastructure packages are module-private** — no other module should import `*.infrastructure.*`.
5. **Domain packages must not reference Spring** — pure Java only.

---

## Alternatives Considered

| Alternative | Why Rejected |
| :--- | :--- |
| **Multi-module Maven** | Too much build complexity for the current team size. Can be adopted later by extracting modules into Maven sub-modules with enforced compile-time boundaries. |
| **Microservices** | Premature. The team is small and the deployment topology doesn't justify distributed systems overhead (network latency, data consistency, deployment orchestration). |
| **Keep flat structure + ArchUnit only** | ArchUnit can enforce rules, but doesn't provide the developer ergonomics of co-located code. Modules-first + ArchUnit is strictly better. |
| **Identical structure for every module** | Rejected — this is just "technical layers repeated inside folders." Simple modules (notes, calendar) don't need 8 sub-packages. |

---

## Consequences

### Positive
- **Code locality**: All task-related code (82 files) lives under `task/`. Developer cognitive load drops significantly.
- **Clear ownership**: Each module has a `package-info.java` documenting its public API, responsibilities, and dependency rules.
- **Boundary enforcement ready**: The structure enables ArchUnit tests and Spring Modulith adoption without further reorganization.
- **Onboarding**: New developers can understand the system by reading 21 `package-info.java` files instead of navigating 377 files across flat directories.

### Negative
- **Initial migration cost**: 377 files moved, 413 import statements updated, 190 cross-module imports auto-resolved. One-time cost.
- **Cross-module entity references**: Some domain entities (User, Organization) are widely referenced. Full decoupling would require events or DTOs at module boundaries — deferred to future work.

### Neutral
- **No runtime behavior change**: This is a pure structural refactoring. Zero business logic was modified. All existing tests pass.

---

## Future Work

1. **ArchUnit tests**: Enforce module boundary rules at compile time (AC-12, AC-13, AC-14).
2. **Spring Modulith**: Evaluate `@ApplicationModule` for automated boundary detection and integration testing.
3. **Facade pattern**: Replace remaining cross-module repository imports with module-level facades/public services.
4. **Event-driven decoupling**: Replace synchronous cross-module calls with domain events where appropriate.
