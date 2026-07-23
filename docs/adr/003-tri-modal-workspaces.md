# ADR-003: Tri-Modal Workspace Isolation

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-20)

## Decision Drivers
- **Data Privacy** — Enterprise organization data must never leak to external crews or personal workspaces.
- **User Clarity** — Users should understand exactly who can see their tasks, notes, and projects.
- **Flexibility** — A single user may simultaneously use personal, crew, and organization features without context-switching to different applications.
- **Security** — Cross-tenant access violations must be architecturally impossible, not just policy-enforced.

## Context
Users want to manage personal items, peer collaboration projects, and enterprise organization work within a single system without cross-tenant data leaks.

## Alternatives Considered

| Alternative | Why Rejected |
| :--- | :--- |
| **Separate applications per mode** | Fragments the user experience. Users need three different logins, UIs, and data stores. |
| **Single unified workspace (no isolation)** | All tasks visible to all collaborators. Privacy violations. Enterprise compliance failure. |
| **Tenant-based isolation (schema-per-org)** | Prevents personal and crew features from existing outside an org boundary. Over-isolates. |
| **Tag-based filtering (soft isolation)** | Isolation relies on query filters, not structural constraints. A missed `WHERE` clause leaks data. |

## Decision
Establish three isolated modes (`PERSONAL`, `CREW`, `ORG`) with structural constraints:
- **Personal** items (`org_id = null`, `crew_id = null`) are strictly private to the creator.
- **Crew** items (`crew_id = {id}`, `org_id = null`) are accessible only by crew members.
- **Organization** items (`org_id = {id}`) form a sealed enterprise vault.
- **Bridge Connection**: Personal projects can be shared with Crews for peer assistance via `ProjectService.shareProjectToCrew()`. Enterprise projects (`project.organization != null`) are strictly blocked from external sharing.
- **Cross-mode dependency prohibition**: Task dependencies are validated to ensure both tasks share the same mode and scope (same org, same crew, or same creator).

## Consequences
### Positive
- Guarantees zero enterprise data leakage to outside crews.
- Provides clarity to end-users regarding data visibility and ownership.
- Bridge connection enables collaboration without compromising isolation.
- Repository-level scoping (e.g., `findByCrewIdAndMode()`) makes violations structurally impossible.

### Negative
- Requires strict validation checks on project sharing and task assignment endpoints.
- Users cannot cross-reference org tasks with crew tasks (by design).
- Bridge connection adds complexity to project permission evaluation.

## Implemented In

| File | Role |
| :--- | :--- |
| `domain/TaskMode.java` | Enum: `PERSONAL`, `CREW`, `ORG` |
| `domain/TaskScope.java` | Scope isolation rules and validation |
| `service/TaskAssignmentServiceImpl.java` | Mode determination during task creation |
| `service/TaskDependencyService.java` | Cross-mode dependency prohibition |
| `service/ProjectService.java` | Bridge connection: `shareProjectToCrew()`, enterprise project blocking |
| `security/TaskPermissionHandler.java` | Mode-aware permission evaluation |
| `security/EmployeeStrategy.java` | Org-scoped access validation |
| `security/SuperAdminStrategy.java` | Personal-only access boundary for platform admins |
