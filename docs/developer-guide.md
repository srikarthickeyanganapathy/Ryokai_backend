# Developer Guide & Domain Glossary

Back to **[Master Index](README.md)**

---

## 1. Module Structure & Where Things Go

The codebase uses a **modular monolith** with 21 bounded-context modules. When adding new code, use this guide to determine the right location.

### Module Tier Quick Reference

| You're building... | Module tier | Structure |
| :--- | :--- | :--- |
| A complex domain with rich workflows (e.g., task management) | **Tier 1** | `api/`, `application/` (command/, query/, orchestration/), `domain/` (model/, strategy/, validation/), `infrastructure/` (persistence/, monitoring/) |
| A medium-complexity feature (e.g., team management) | **Tier 2** | `api/`, `application/`, `domain/`, `dto/`, `infrastructure/persistence/` |
| A simple CRUD feature (e.g., notes) | **Tier 3** | Flat — all files in one package |

### Key Architectural Rules

- **Identity** (authentication business logic) is separate from **Security** (JWT/filter infrastructure)
- `TaskStrategyFactory` lives in `task/application/strategy/` — it's orchestration, not infrastructure
- Domain events live in `shared/events/` — they are internal mechanisms, not external integrations
- External adapters (email, websocket) live in `integration/`

---

## 2. How to Add a New Task State Action (Step-by-Step)

1. **Enum**: Add the new status to `TaskStatus` in `task/domain/model/TaskStatus.java`.
2. **Strategy Method**: Define transition rules in `task/domain/strategy/TaskLifecycleStrategy.java` and implement in `OrgTaskStrategy`, `CrewTaskStrategy`, or `PersonalTaskStrategy` (all in `task/domain/strategy/`).
3. **Service Layer**: Add the transition method to `task/application/command/TaskStateTransitionServiceImpl.java`.
4. **Controller Method**: Add `@PostMapping("/{taskId}/your-action")` in `task/api/TaskStateController.java` with `@PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")`.
5. **Audit Event**: Log the state change via `task/application/orchestration/TaskAuditService.logStatusChange(...)`.
6. **Notification**: Add notification event to `notification/event/NotificationEvent.java` and create dispatch in `task/application/orchestration/TaskNotificationService.java`.
7. **Domain Event** (optional): If other components need to react, publish a `TaskStatusChangedEvent` (in `task/event/`) via `DomainEventPublisher` (in `shared/events/`).
8. **Migration**: If new database columns are needed, add `V{N}__description.sql` in `db/migration/`.

---

## 3. How to Add a New Permission

1. **Enum**: Add the permission name to `security/PermissionType.java`.
2. **DataSeeder**: `bootstrap/DataSeeder.run()` automatically seeds new enum values on startup — no manual SQL needed.
3. **Handler**: Add the permission check logic to the appropriate handler in the owning module (e.g., `task/security/TaskPermissionHandler.java`, `project/security/ProjectPermissionHandler.java`).
4. **PermissionService**: Wire the new permission into `organization/rbac/application/PermissionService.java` policies or `RolePermissionScope` mapping.
5. **Controller**: Use in `@PreAuthorize("hasPermission(#id, 'EntityType', 'PERMISSION_NAME')")`.

---

## 4. How to Add a New Controller

1. **Class**: Create your controller in the appropriate module's `api/` package (e.g., `goal/api/GoalController.java`). For Tier 3 modules, place it directly in the module package.
2. **User Resolution**: Inject `UserService` from `user/application/`, use `getCurrentUser(userDetails.getUsername())` to resolve the authenticated `User` entity.
3. **Service**: Create your service in the module's `application/` package (e.g., `goal/application/GoalService.java`).
4. **DTOs**: Create request/response DTOs in the module's `dto/` package (Tier 2) or `api/request/` and `api/response/` packages (Tier 1) with Bean Validation annotations.
5. **Permissions**: Add `@PreAuthorize` annotations using SpEL permission expressions.
6. **Tests**: Unit test with `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`.

---

## 5. How to Add a New Module

1. **Assess Complexity**: Determine if the module is Tier 1, 2, or 3 based on domain complexity.
2. **Create Package**: Add a new package under `com.example.taskflow.<module_name>/`.
3. **Create `package-info.java`**: Document the module's responsibilities, public API, and dependency rules.
4. **Follow the Tier Pattern**:
   - **Tier 3**: Place all files flat in the module package.
   - **Tier 2**: Create `api/`, `application/`, `domain/`, `dto/` sub-packages.
   - **Tier 1**: Create full hexagonal structure with `infrastructure/persistence/`.
5. **Cross-Module Access**: Only expose application services as the public API. Other modules must NOT import your repositories directly.
6. **Update Docs**: Add the module to `architecture.md` module overview and dependency graph.

---

## 6. Testing Infrastructure Guide

### Testing Stack Specifications
- **Unit Testing**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`).
- **Integration Testing**: `@SpringBootTest` with `@AutoConfigureMockMvc`.
- **Repository Data Layer**: `@DataJpaTest` with H2 in-memory test database.
- **Security Testing**: `@WithMockUser(username = "admin", roles = {"ADMIN"})`.

### Example Test Class Pattern

```java
@ExtendWith(MockitoExtension.class)
class TaskAssignmentServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskHierarchyValidator hierarchyValidator;
    @InjectMocks private TaskAssignmentServiceImpl taskAssignmentService;

    @Test
    void assignTask_ShouldThrowException_WhenRolePriorityIsViolated() {
        doThrow(new UnauthorizedActionException("Role priority violation"))
            .when(hierarchyValidator).validateOrgOrTeamTask(any(), any(), any(), anyBoolean());

        assertThrows(UnauthorizedActionException.class, () -> 
            taskAssignmentService.assignTask(command));
    }
}
```

### Import Paths for Test Classes

| Class | Package |
| :--- | :--- |
| `TaskAssignmentServiceImpl` | `com.example.taskflow.task.application.command` |
| `TaskHierarchyValidator` | `com.example.taskflow.task.domain.validation` |
| `UnauthorizedActionException` | `com.example.taskflow.shared.exception` |
| `TaskRepository` | `com.example.taskflow.task.infrastructure.persistence` |

---

## 7. Exception & Error Catalogue

| Exception Class | Module | HTTP Code | Error Code | Root Cause / Scenario |
| :--- | :--- | :--- | :--- | :--- |
| `ResourceNotFoundException` | `shared.exception` | `404` | `RESOURCE_NOT_FOUND` | Requested generic entity ID does not exist in DB |
| `TaskNotFoundException` | `task.exception` | `404` | `TASK_NOT_FOUND` | Requested task ID does not exist |
| `UserNotFoundException` | `user.exception` | `404` | `USER_NOT_FOUND` | Requested user ID or username does not exist |
| `CrewNotFoundException` | `crew.exception` | `404` | `CREW_NOT_FOUND` | Requested crew ID does not exist |
| `CrewFullException` | `crew.exception` | `400` | `CREW_FULL` | Attempting to join a crew that has reached capacity |
| `CrewInviteExpiredException` | `crew.exception` | `400` | `INVITE_EXPIRED` | Attempting to accept an expired crew invite |
| `UsernameConflictException` | `user.exception` | `409` | `USERNAME_CONFLICT` | Registration or profile update with existing username/email |
| `InvalidCredentialsException` | `security.exception` | `401` | `INVALID_CREDENTIALS` | Invalid login credentials provided |
| `UnauthorizedActionException` | `shared.exception` | `403` | `UNAUTHORIZED_ACTION` | Role priority violation or cross-org action |
| `OrganizationSuspendedException` | `organization.core.exception` | `403` | `ORGANIZATION_SUSPENDED` | Action attempted on a suspended organization |
| `IllegalStateException` | JDK | `409` | `INVALID_STATE` | Submitting task without evidence or invalid state jump |
| `AccessDeniedException` | Spring Security | `403` | `ACCESS_DENIED` | Insufficient permissions for the operation |
| `TokenRefreshException` | `security.exception` | `401` | `TOKEN_REFRESH_ERROR` | Expired, revoked, or reused refresh token |
| `OptimisticLockingFailureException` | Hibernate/JPA | `409` | `OPTIMISTIC_LOCK_CONFLICT` | Concurrent modification of Task/Project/ChecklistItem |

### Structured Error Response Format

All errors return consistent JSON:
```json
{
  "timestamp": "2026-07-23T09:15:00.000+00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Role priority violation: cannot assign tasks to users with higher priority",
  "code": "UNAUTHORIZED_ACTION",
  "path": "/api/tasks/assign",
  "correlationId": "abc-123-def-456"
}
```

---

## 8. Verification Levels

When documenting or reviewing implementation details, use these labels to distinguish confidence levels:

| Label | Meaning |
| :--- | :--- |
| ✅ **Verified** | Confirmed by reading the actual source code |
| 🔍 **Observed** | Seen in code but not exercised (e.g., config exists but untested in production) |
| ⚠️ **Needs Verification** | Inferred from naming conventions, comments, or partial code — requires further investigation |
| ❌ **Not Implemented** | Referenced in comments/docs but no implementation found in codebase |

---

## 9. Domain Glossary

| Term | Definition |
| :--- | :--- |
| **Bounded Context** | A module boundary that encapsulates a cohesive domain concept with its own API, application logic, domain model, and persistence. |
| **Crew** | A flat, peer-to-peer collaboration workspace without organizational hierarchy or manager sign-off. |
| **Organization** | A multi-tenant enterprise vault boundary governing custom RBAC roles, teams, OKRs/Goals, and HR leave requests. |
| **TaskMode** | Enum (`PERSONAL`, `CREW`, `ORG`) defining which lifecycle strategy and business rules govern a task. |
| **Role Priority** | An integer rank (0–100) assigned to an organization `Role`. Lower values = higher authority. |
| **TaskEvidence** | File, link, or text proof submitted by an assignee when moving an Organization task to `SUBMITTED` status. |
| **TeamObserver** | A read-only auditor role assigned to a department `Team` allowing task and activity feed inspection without mutation rights. |
| **Goal / OKR** | An Organization-scoped strategic objective container containing measurable key results. |
| **Token Version** | An integer field on `User` that is incremented to mass-invalidate all outstanding JWT access tokens. |
| **Correlation ID** | A UUID propagated via `X-Correlation-Id` header and MDC context for end-to-end request tracing across async boundaries. |
| **Project Bridge** | The mechanism allowing personal projects to be shared with Crews. Enterprise projects are explicitly blocked from this bridge. |
| **Optimistic Lock** | `@Version` field on `Task`, `Project`, `ChecklistItem` — prevents silent overwrites on concurrent edits. |
| **Shared Kernel** | The `shared/` package — minimal set of types (events, exceptions, utilities) genuinely needed by multiple modules. |
| **Module Tier** | Classification of modules by complexity: Tier 1 (hexagonal), Tier 2 (layered), Tier 3 (flat). |
| **Application Service** | The public API of a module — the only entry point for cross-module communication. |
