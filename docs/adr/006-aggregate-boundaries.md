# ADR-006: Domain Aggregate Boundaries

Back to **[ADR Index](README.md)**

---

## Status
**Proposed** (2026-07-23)

## Decision Drivers
- **Consistency** — Prevent accidental cross-entity mutations that violate transactional boundaries.
- **Evolvability** — Enable future CQRS/event sourcing by establishing clear aggregate event streams.
- **Developer Clarity** — New engineers need to know which entities can be modified together.
- **Performance** — Prevent unnecessarily large transaction scopes that hold database locks.

## Context
As the domain model grows beyond 30+ entities, it becomes critical to define which entities form logical aggregates — groups of entities that are modified together within a single transaction boundary. Without explicit aggregate boundaries, developers risk creating cross-aggregate mutations that break consistency guarantees and make the system harder to reason about.

## Alternatives Considered

| Alternative | Why Not Selected |
| :--- | :--- |
| **No explicit boundaries (status quo)** | Works at current scale but creates technical debt. New developers create cross-aggregate joins without realizing the implications. |
| **Strict DDD aggregates with aggregate root enforcement** | Requires repository-per-aggregate refactor and prohibiting direct child entity repositories. Too restrictive for current Spring Data JPA patterns. |
| **Module boundaries (Java modules / packages)** | Architectural modules enforce compile-time isolation but don't map directly to transactional boundaries. Complementary, not a replacement. |

Currently, the codebase implicitly respects aggregate boundaries (e.g., `ChecklistService` always loads the parent `Task` before modifying checklist items), but these boundaries are not documented or enforced architecturally.

## Decision
Define five aggregate roots with explicit transactional boundaries:

1. **Task Aggregate** (root: `Task`) — includes `ChecklistItem`, `TaskEvidence`, `TaskComment`, `TaskDependency`, `TaskStatusHistory`.
2. **Project Aggregate** (root: `Project`) — includes collaborators and shared crew references. Tasks reference projects via FK but are NOT part of this aggregate.
3. **Organization Aggregate** (root: `Organization`) — includes `Role`, `Permission`, `Team`, `TeamMember`, `TeamObserver`, `OrganizationMembership`, `Announcement`, `Goal`, `KeyResult`, `LeaveRequest`.
4. **Crew Aggregate** (root: `Crew`) — includes `CrewMember`, `CrewChannel`, `CrewMessage`, `CrewInvite`, `Whiteboard`.
5. **User Aggregate** (root: `User`) — includes `RefreshToken`, `Note`, `FocusSession`, `CalendarEvent`, `SavedItem`, `Notification`.

### Cross-Aggregate Reference Rules
- Cross-aggregate references are by foreign key ID only (not by object graph navigation in transactions).
- Deleting a Project sets `task.project_id = null` (soft detachment) — never cascade-deletes tasks.
- Organization soft-delete (`deleted = true`) does not cascade to tasks; tasks become orphaned and inaccessible.

## Consequences
### Positive
- Prevents accidental cross-aggregate mutations (e.g., modifying an Organization entity inside a Task transaction).
- Makes future CQRS/event sourcing migration feasible — each aggregate can have its own event stream.
- Clarifies service boundaries for new developers.

### Negative
- Requires discipline to avoid "convenient" cross-aggregate JPA navigations (e.g., `task.getOrganization().getRoles()` inside a Task service).
- May require additional repository queries where JPA `@ManyToOne` eager fetch was previously used.
