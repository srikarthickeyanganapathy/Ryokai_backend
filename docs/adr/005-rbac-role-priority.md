# ADR-005: Custom RBAC & Role Priority Hierarchy

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-21)

## Context
In corporate organizations, authority is non-flat. Lower-ranked personnel must be prevented from assigning or modifying tasks belonging to senior executives.

## Decision
Introduce an integer `priority` rank (0–100) on custom `Role` entities. Enforce priority checks in `TaskHierarchyValidator`:
- Assignors must have equal or higher role priority than assignees.
- Managers cannot modify roles or remove members with higher role priority than themselves.

## Consequences
### Positive
- Enforces corporate governance and prevents vertical privilege escalation.
- Flexible custom priority ranking per organization tenant.

### Negative
- Role priority calculations add DB lookups during task assignment and member management operations.
