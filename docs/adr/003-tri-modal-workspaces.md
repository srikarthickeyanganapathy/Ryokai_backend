# ADR-003: Tri-Modal Workspace Isolation

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-20)

## Context
Users want to manage personal items, peer collaboration projects, and enterprise organization work within a single system without cross-tenant data leaks.

## Decision
Establish three isolated modes (`PERSONAL`, `CREW`, `ORG`):
- Personal items (`org_id = null`, `crew_id = null`) are strictly private.
- Crew items (`crew_id = {id}`, `org_id = null`) are accessible only by crew members.
- Organization items (`org_id = {id}`) form a sealed enterprise vault.
- Bridge Connection: Personal projects can be shared with Crews for peer assistance via `ProjectService.shareProjectToCrew()`. Enterprise projects (`project.organization != null`) are strictly blocked from external sharing.

## Consequences
### Positive
- Guarantees zero enterprise data leakage to outside crews.
- Provides clarity to end-users regarding data visibility and ownership.

### Negative
- Requires strict validation checks on project sharing and task assignment endpoints.
