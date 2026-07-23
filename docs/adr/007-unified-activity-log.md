# ADR-007: Unified Activity Log vs Separate Entity Logs

Back to **[ADR Index](README.md)**

---

## Status
**Proposed** (2026-07-23)

## Context
The system currently has two separate activity log tables with nearly identical schemas:

- `task_activity_logs` → `TaskActivityLog` entity
- `project_activity_logs` → `ProjectActivityLog` entity

Both contain: `actionType`, `entityType`, `entityId`, `metadataJson` (JSONB), `source`, `ipAddress`, `userAgent`, `correlationId`.

As the system grows, additional entity types will need activity logging: Goals, Organizations, Crews, Announcements, LeaveRequests. Each new entity type currently requires a new table, repository, service, and controller.

## Decision
**Under Review** — Two options are being evaluated:

### Option A: Unified `activity_logs` Table (Recommended)
Single table with `entity_type` discriminator column. New entity types get activity logging for free. Single query for "show me everything User X did today."

**Trade-off**: Table grows faster. Requires partitioning by `created_at` for high-volume deployments (>1M rows).

### Option B: Keep Separate Tables (Current)
Each entity type maintains its own activity log table. Different indexes optimized per access pattern.

**Trade-off**: Duplicated schema, duplicated service logic, each new entity type requires a new table + repository + service.

## Recommendation
Unify if activity logging will expand to more than 3 entity types. Keep separate if Task and Project will remain the only audited entities for the foreseeable future.

## Consequences (If Unified)
### Positive
- Single audit infrastructure for all entity types.
- One service, one repository, one controller for all activity queries.
- Cross-entity audit trail: "What happened in this organization today?"

### Negative
- Table growth — requires partitioning or time-based archival.
- Slightly more complex queries (must always filter by `entity_type`).
- Migration effort: data migration from existing tables + code refactor.
