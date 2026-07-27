# ADR 009: Platform Administration & Tenant Workspace Architecture

## Status
Accepted (10/10 Gold Standard)

## Decision Drivers
- Need for clear architectural separation between global platform governance (Control Plane) and collaborative tenant workspaces (Data Plane).
- Preventing namespace contamination where global platform endpoints and tenant workspace endpoints reside in the same URL path or controller.
- Establishing unambiguous ownership rules for business entities (`User`, `Organization`, `Membership`, `Task`, etc.).
- Decoupling backend security authorization from frontend navigation shell selection (`PlatformShell` vs. `TenantShell`).
- Ensuring that structural refactoring preserves 100% of existing functional runtime behavior and relational database compatibility.

## Context
During the backend architecture audit, we analyzed whether Ryokai's backend correctly separates the **Platform Control Plane** from the **Tenant Data Plane**. While the collaborative tenant domain was scored highly (8.5–9 / 10) due to robust mode-specific evaluation pipelines (`AuthorizationPipeline`, `PermissionService`) and workspace isolation, we discovered several areas of structural ambiguity:
1. **Contaminated Namespaces:** Endpoints like `/api/v1/admin/roles` accepted both global roles (`orgId == null`) and tenant workspace roles (`orgId != null`), blurring plane boundaries.
2. **Coupling to Navigation State:** Early proposals suggested gating Control Plane APIs behind an explicit `applicationType == PLATFORM` token claim or header, which improperly tied backend authorization to frontend shell routing.
3. **Unclear Entity Ownership:** The boundary between global account administration and workspace membership needed explicit definition to prevent cross-plane service dependencies.

## Decision
We are adopting a **Platform Administration & Tenant Workspace Architecture** that elevates the Control Plane into a first-class citizen while preserving the existing, mature Data Plane.

### 1. Executive Vision & Core Principle
> *“Ryokai is a multi-tenant SaaS collaboration platform with a shared identity model, a dedicated Platform Administration layer (Control Plane), organization-scoped workspaces (Data Plane), and a strict commitment that architectural evolution must preserve business behavior.”*

```
                    Ryokai Platform
                           │
            ┌──────────────┴──────────────┐
            │                             │
            ▼                             ▼
 Platform Administration         Organization Workspaces
      Control Plane                   Data Plane
            │                             │
            └──────────────┬──────────────┘
                           │
                    Shared Identity
```

### 2. Three-Way Operational Ownership
Operational responsibilities are strictly partitioned across three distinct layers:

```
        ┌────────────────────────────────────────────────────────┐
        │             Shared Infrastructure (Auth)               │
        │      • JWT & BCrypt Hashing   • Token Chains           │
        │      • Session Management     • MFA (Future)           │
        └───────────────────────────┬────────────────────────────┘
                                    │
            ┌───────────────────────┴───────────────────────┐
            ▼                                               ▼
┌───────────────────────┐                       ┌───────────────────────┐
│Platform Administration│                       │Organization Workspaces│
│    (Control Plane)    │                       │     (Data Plane)      │
│                       │                       │                       │
│ • User Account        │                       │ • Workspace           │
│   Lifecycle (Suspend) │                       │   Memberships         │
│ • Organization        │                       │ • Workspace Operations│
│   Lifecycle (Suspend) │                       │   (Invites, Teams)    │
│ • System Health       │                       │ • Collaborative Work  │
│ • Global Audit        │                       │   (Projects, Tasks)   │
└───────────────────────┘                       └───────────────────────┘
```

| Operational Layer | Strictly Owns | Never Owns |
| :--- | :--- | :--- |
| **Shared Infrastructure**<br>*(Authentication & Identity Mechanics)* | • Password hashing (BCrypt) & token signing (JJWT)<br>• Authentication mechanics (Login, Register, Refresh, Logout)<br>• Session persistence & token denylist management<br>• Future authentication mechanisms (MFA, SSO) | • Business role assignments or permission evaluations<br>• Organization lifecycle or workspace hierarchies<br>• Task collaboration or project activity |
| **Platform Administration**<br>*(Control Plane)* | • **User Account Lifecycle:** Global user directory, account lock/unlock, account suspension, soft-deletion.<br>• **Organization Lifecycle:** Platform owns the global lifecycle of organizations (activation, suspension, deletion, limits), while organization creation may occur through approved tenant onboarding workflows.<br>• **Platform Governance:** CORS, environment limits, licensing, system health (Actuator), Prometheus metrics, global audit logs, and security policies. | • **Workspace Operations:** Member invitations, team creation, workspace custom role definitions, or org settings.<br>• **Collaborative Work:** Tasks, checklists, dependencies, evidence, comments, projects, whiteboards, calendar events, or notes. |
| **Organization Workspace**<br>*(Data Plane)* | • **Workspace Operations:** Member invitations, team management, workspace roster governance, org custom role definitions, and org settings.<br>• **Collaborative Work:** Projects, tasks, task state transitions, review chains, evidence uploads, checklists, activity feeds, whiteboards, notes, calendar events, and focus sessions. | • Global user account tables or identity mechanics<br>• Organization suspension or reactivation<br>• System health, actuator metrics, or CORS rules<br>• Platform licensing or global security policies |

### 3. Domain Ownership Principle
Every business capability has one primary owning domain. Neither plane should directly depend on or bypass the business services owned by the other (**Cross-Plane Independence**).

| Business Entity / Concern | Owning Domain | Architectural Mandate |
| :--- | :--- | :--- |
| `User` (Account Entity) | **Platform Administration** | Managed globally. Orgs can invite users to memberships, but cannot suspend or delete global user accounts. |
| `Organization` (Lifecycle) | **Platform Administration** | Platform owns org existence, status (`ACTIVE`, `SUSPENDED`, `DELETED`), and platform limits. |
| `Organization` (Operations) | **Organization Workspace** | Workspace admins own internal org settings, member invitations, and team structures. |
| `OrganizationMembership` | **Organization Workspace** | Represents a user's collaborative role within a specific workspace. |
| `Role` & `Permission` (Tenant) | **Organization Workspace** | Custom role definitions and permission assignments are strictly scoped by `organizationId`. |
| `Project`, `Task`, `Crew` | **Organization Workspace** | Collaborative domain objects belong 100% to the workspace and are protected by privacy boundaries. |
| `Calendar`, `Whiteboard`, `Note`| **Organization Workspace** | Real-time and personal/peer productivity tools execute entirely within the Data Plane. |

### 4. Target Control Plane Architecture & Capability Hints
The backend decouples **Navigation Context from Security Authorization**, allowing the frontend `<PlatformShell>` and `<TenantShell>` to route cleanly while keeping backend security stateless and capability-driven.

```
                       Authentication
                             │
                             ▼
                   Unified Identity (User)
                             │
                             ▼
                       Authorization
                             │
                             ▼
                     Navigation Hints
      { isSuperAdmin: true, availableApplications: [...] }
```

1. **Derived Navigation Hints (`/api/v1/users/me/sessions`):**
   When resolving sessions, the backend computes navigation hints from the user's underlying authorization state:
   ```json
   {
     "user": {
       "id": 1,
       "username": "owner@ryokai.com",
       "isSuperAdmin": true
     },
     "availableApplications": ["PLATFORM", "TENANT"],
     "defaultApplication": "PLATFORM"
   }
   ```
   * **Security Invariant:** `availableApplications` and `defaultApplication` are derived from server-side authorization state and **must never be trusted as client-provided authorization inputs**. They exist solely to guide the frontend `RouteResolver`.
2. **Stateless Security Enforcement:**
   * **Control Plane (`/api/v1/platform/**`):** Requires Platform authorization. *Platform authorization is currently implemented using the global `SUPER_ADMIN` authority.*
   * **Data Plane (`/api/v1/organizations/**`):** Requires an active `OrganizationMembership` and valid Organization RBAC permissions. Every tenant operation executes within an Organization scope, whether provided explicitly in the request URL or resolved indirectly through the target resource (e.g., resolving `task.getOrg()` from `/api/v1/tasks/{id}`).

### 5. Compiler-Enforced Package Hierarchy
To make the Control/Data plane boundary structural and obvious during development, controllers and services are organized by operational domain:

```
com.example.taskflow/
├── controller/
│   ├── platform/
│   │   ├── PlatformOrganizationController.java  (/api/v1/platform/organizations)
│   │   ├── PlatformUserController.java          (/api/v1/platform/users)
│   │   ├── PlatformRoleController.java          (/api/v1/platform/roles)
│   │   └── PlatformHealthController.java        (/actuator/**)
│   ├── organization/
│   │   ├── OrganizationController.java          (/api/v1/organizations)
│   │   ├── OrganizationRoleController.java      (/api/v1/organizations/{id}/roles)
│   │   └── OrganizationMembershipController.java(/api/v1/organizations/{id}/members)
│   ├── project/
│   └── task/
├── service/
│   ├── platform/
│   │   ├── PlatformOrganizationService.java
│   │   └── PlatformUserService.java
│   └── organization/
│       ├── OrganizationService.java
│       └── OrganizationRoleService.java
```

### 6. Functional Behavior Preservation
This initiative is an **architectural refactoring**, not a feature rewrite. The following workflows must preserve their exact runtime behavior:
1. **Authentication & Identity Workflows:** Login, Registration, Email Verification, Token Refresh Chains, Logout, and Password Reset remain structurally and functionally identical.
2. **Tenant SaaS Workflows:** Organization operations, Projects, Task lifecycle, Dependencies, Evidence, Comments, Checklists, Activity history, Dashboard stats, Calendar, Notes, Whiteboards, and Focus sessions continue functioning exactly as they do today.
3. **Organization RBAC:** Built-in roles (Admin, Manager, Member, Viewer), Custom Role creation, permission assignments, and SpEL permission evaluations remain untouched in their business rules and hierarchy.
4. **Platform Capabilities:** Organization lifecycle management (Suspend, Activate, Delete) and system monitoring continue executing their existing underlying domain logic under the new `/api/v1/platform/**` namespace.
5. **Database Model Compatibility:** The core relational schema (`User`, `Role`, `Organization`, `OrganizationMembership`, `Project`, `Task`) remains untouched. Logical plane separation replaces physical database separation.

### 7. Out of Scope
* **No New RBAC Engine:** We will not build Platform RBAC (`SUPPORT`, `BILLING`, `AUDITOR`) in this phase. This decision does not prevent future introduction of Platform RBAC if operational requirements evolve.
* **No New Authentication Provider:** Existing BCrypt and JWT security infrastructure remains as-is.
* **No Database Redesign or Splitting:** We will not create separate tables for platform users or migrate to multi-database tenancy.
* **No Microservice Migration:** The backend remains a cohesive, modular monolithic application.
* **No Workflow Redesigns:** Organization operations, Task lifecycles, and Collaborative workflows will not be redesigned.

### 8. Verification & Backward Compatibility Mandate
1. **Backward-Compatible Contracts:** Existing request and response schemas must remain backward compatible. New fields (e.g., `availableApplications`) may be added, but existing fields, meanings, and behaviors must not change without an intentional API versioning decision.
2. **Behavioral Parity:** Regression suites must verify identical HTTP status codes, database mutations, workflow state transitions, security audit records, and WebSocket broadcast events before and after the refactoring.

### 9. Future Consideration: Platform Support Sessions
This architecture naturally accommodates future enterprise support tooling without breaking domain boundaries:
* **Support Protocol:** A Platform Operator initiates a **Platform Support Session** (via delegated access or break-glass impersonation) to temporarily operate inside an Organization Workspace.
* **Identity Preservation:** The operator enters the workspace **without changing their underlying user identity**, preventing privilege confusion.
* **Auditability:** Every read and write performed during a support session is tagged with explicit break-glass audit headers and recorded in global security audit logs.
* **Boundary Integrity:** Because backend authorization checks capabilities rather than frontend navigation tags, legitimate support requests are processed cleanly by Data Plane evaluators without requiring permanent global bypass rules.

## Consequences
- **Positive:** Unambiguous separation between Control Plane and Data Plane in both URL namespaces and package structures.
- **Positive:** Zero disruption to existing tenant users, database schemas, or collaborative business workflows.
- **Positive:** Eliminates risk of privilege escalation or permission bleeding across workspaces and platform layers.
- **Positive:** Prepares the backend for future scalability, enterprise support sessions, and dedicated platform admin shells.

## Implemented In
- `com.example.taskflow.controller.platform.*` (Implemented: `PlatformOrganizationController`, `PlatformRoleController`, `PlatformUserController` mapped to `/api/v1/platform/*`)
- `com.example.taskflow.controller.organization.*` (Implemented: `OrganizationController`, `OrganizationRoleController`, `OrganizationMembershipController`, `OrganizationInviteController`, `OrganizationTeamController` mapped to `/api/v1/organizations/*`)
- `com.example.taskflow.service.platform.*` (Implemented: `PlatformOrganizationService`, `PlatformUserService` for global Control Plane governance)
- `com.example.taskflow.service.organization.*` (Implemented: `OrganizationService`, `OrganizationRoleService` for Data Plane workspace operations)
- `com.example.taskflow.service.*` (Shared identity and cross-cutting business capabilities: `UserService`, `RoleService`, `AuditService`)
- `com.example.taskflow.architecture.*` (Verified: `PlatformTenantBoundaryTest` automated test suite confirming zero dependency contamination and bean registration integrity)
