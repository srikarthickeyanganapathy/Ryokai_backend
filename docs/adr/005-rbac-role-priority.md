# ADR-005: Custom RBAC & Role Priority Hierarchy

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-21)

## Decision Drivers
- **Enterprise Governance** — Corporate hierarchies require non-flat authority models.
- **Security** — Lower-ranked personnel must be prevented from escalating privileges or managing superiors.
- **Flexibility** — Each organization defines its own role names and priority rankings.
- **Audit Compliance** — Task assignment and approval chains must be verifiably correct.

## Context
In corporate organizations, authority is non-flat. Lower-ranked personnel must be prevented from assigning or modifying tasks belonging to senior executives. Standard RBAC (role-based access control) with fixed roles like `ADMIN`, `USER`, `MANAGER` doesn't support the variety of hierarchies across different organizations.

## Alternatives Considered

| Alternative | Why Rejected |
| :--- | :--- |
| **Fixed Spring Security roles** (`ROLE_ADMIN`, `ROLE_USER`) | Cannot model organization-specific hierarchies. Director/VP/Lead/Intern distinctions impossible. |
| **Hierarchical role inheritance** (role → parent role) | Complex tree traversal for every permission check. Difficult to reason about inherited permissions. |
| **Attribute-Based Access Control (ABAC)** | Powerful but complex. Requires policy engine (e.g., OPA). Over-engineered for current scale. |
| **Level-based (integer) without custom roles** | Does not allow naming or describing roles. Poor UX for organization admins. |

## Decision
Introduce an integer `priority` rank (0–100) on custom `Role` entities. Enforce priority checks in `TaskHierarchyValidator`:
- Assignors must have equal or higher role priority (lower integer value = higher authority) than assignees.
- Managers cannot modify roles or remove members with higher role priority than themselves.
- Reviewers must have **strictly higher authority** (lower priority value) than the assignee — equal priority is NOT sufficient for review.
- Self-review is explicitly forbidden regardless of role priority.

Each organization defines its own roles with custom names and priority values:
```
Director  (priority: 10) — highest authority
Manager   (priority: 50)
Lead      (priority: 70)
Member    (priority: 90) — lowest authority
```

## Consequences
### Positive
- Enforces corporate governance and prevents vertical privilege escalation.
- Flexible custom priority ranking per organization tenant.
- Priority math is simple and fast — integer comparison, no tree traversal.
- Organizations can define as many roles as needed (not limited to fixed set).

### Negative
- Role priority calculations add DB lookups during task assignment and member management operations.
- Priority conflicts possible if admin assigns same priority to different roles — needs UI-level guidance.
- No role inheritance — permissions must be explicitly assigned to each role.

## Implemented In

| File | Role |
| :--- | :--- |
| `domain/Role.java` | Entity: `priority` (int), `name`, `description`, org-scoped |
| `domain/Permission.java` | Granular permission tokens |
| `security/PermissionType.java` | Enum: 19 permission types |
| `service/TaskHierarchyValidator.java` | Priority comparison during assignment/reassignment |
| `service/RoleService.java` | Role CRUD, priority validation, permission assignment |
| `security/EmployeeStrategy.java` | Role-priority-aware permission evaluation |
| `security/CustomPermissionEvaluator.java` | SpEL evaluator delegating to handlers |
| `security/OrganizationPermissionHandler.java` | Org-scoped permission checks |
| `config/DataSeeder.java` | Bootstrap `PermissionType` values on startup |
| `controller/OrganizationRoleController.java` | Role management endpoints |
