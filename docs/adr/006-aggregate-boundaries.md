# ADR-006: Domain Aggregate Boundaries

Back to **[ADR Index](README.md)**

---

## Status
**Proposed** (2026-07-23)

## Context
As the domain model grows beyond 30+ entities, it becomes critical to define which entities form logical aggregates — groups of entities that are modified together within a single transaction boundary. Without explicit aggregate boundaries, developers risk creating cross-aggregate mutations that break consistency guarantees and make the system harder to reason about.

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
