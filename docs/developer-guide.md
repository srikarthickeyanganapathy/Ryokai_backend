# Developer Guide & Domain Glossary

Back to **[Master Index](README.md)**

---

## 1. How to Add a New Task State Action (Step-by-Step)

1. **Enum**: Add the new status to `TaskStatus` in `domain/TaskStatus.java`.
2. **Strategy Method**: Define transition rules in `TaskLifecycleStrategy` and implement in `OrgTaskStrategy`, `CrewTaskStrategy`, or `PersonalTaskStrategy`.
3. **Service Layer**: Add the transition method to `TaskStateTransitionService` and implement in `TaskStateTransitionServiceImpl`.
4. **Controller Method**: Add `@PostMapping("/{taskId}/your-action")` in `controller/TaskStateController.java` with `@PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")`.
5. **Audit Event**: Log the state change via `TaskAuditService.logStatusChange(...)`.
6. **Notification**: Add notification event to `NotificationEvent` enum and create dispatch in `TaskNotificationService`.
7. **Domain Event** (optional): If other components need to react, publish a `TaskStatusChangedEvent` via `ApplicationEventPublisher`.
8. **Migration**: If new database columns are needed, add `V{N}__description.sql` in `db/migration/`.

---

## 2. How to Add a New Permission

1. **Enum**: Add the permission name to `PermissionType` enum.
2. **DataSeeder**: `DataSeeder.run()` automatically seeds new enum values on startup — no manual SQL needed.
3. **Handler**: Add the permission check logic to the appropriate handler (`TaskPermissionHandler`, `OrganizationPermissionHandler`, etc.).
4. **EmployeeStrategy**: Wire the new permission into `EmployeeStrategy` so that role-based checks resolve correctly.
5. **Controller**: Use in `@PreAuthorize("hasPermission(#id, 'EntityType', 'PERMISSION_NAME')")`.

---

## 3. How to Add a New Controller

1. **Class**: Create `controller/YourController.java` annotated with `@RestController`, `@RequestMapping`, `@Validated`.
2. **User Resolution**: Inject `UserService`, use `getCurrentUser(userDetails.getUsername())` to resolve the authenticated `User` entity.
3. **Service**: Create `service/YourService.java` (interface) and `service/YourServiceImpl.java` (implementation).
4. **DTOs**: Create request/response DTOs in `dto/` with Bean Validation annotations (`@NotBlank`, `@Size`, `@Min`, etc.).
5. **Permissions**: Add `@PreAuthorize` annotations using SpEL permission expressions.
6. **Tests**: Unit test with `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`.

---

## 4. Testing Infrastructure Guide

### Testing Stack Specifications
- **Unit Testing**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`).
- **Integration Testing**: `@SpringBootTest` with `@AutoConfigureMockMvc`.
- **Repository Data Layer**: `@DataJpaTest` with H2 in-memory test database.
- **Security Testing**: `@WithMockUser(username = "admin", roles = {"ADMIN"})`.

### Example Test Class Pattern (`TaskAssignmentServiceTest.java`)
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

---

## 5. Exception & Error Catalogue

| Exception Class | Thrown By Component | HTTP Code | Error Code | Root Cause / Scenario |
| :--- | :--- | :--- | :--- | :--- |
| `ResourceNotFoundException` | `TaskRepository`, `UserRepository` | `404` | `RESOURCE_NOT_FOUND` | Requested entity ID does not exist in DB |
| `UnauthorizedActionException` | `TaskHierarchyValidator`, `OrgTaskStrategy` | `403` | `UNAUTHORIZED_ACTION` | Role priority violation or cross-org action |
| `InvalidStateTransitionException` | `OrgTaskStrategy`, `TaskStateTransitionServiceImpl` | `400` | `INVALID_STATE_TRANSITION` | Submitting task without evidence or invalid state jump |
| `AccessDeniedException` | `CustomPermissionEvaluator`, `ProjectService` | `403` | `ACCESS_DENIED` | Insufficient permissions for the operation |
| `MethodArgumentNotValidException` | Spring MVC Validation Layer | `400` | `VALIDATION_ERROR` | `@NotBlank`, `@Size`, or `@Min` constraint violation |
| `IllegalArgumentException` | `PersonalTaskStrategy`, `TaskAssignmentServiceImpl` | `400` | `ILLEGAL_ARGUMENT` | Assigning personal task to another user |
| `TokenRefreshException` | `RefreshTokenService` | `401` | `TOKEN_REFRESH_ERROR` | Expired, revoked, or reused refresh token |
| `OptimisticLockingFailureException` | Hibernate/JPA | `409` | `OPTIMISTIC_LOCK_CONFLICT` | Concurrent modification of Task/Project/ChecklistItem |
| `DataIntegrityViolationException` | Hibernate/JPA | `409` | `DATA_INTEGRITY_VIOLATION` | Unique constraint violation (duplicate username/email) |
| `HttpRequestMethodNotSupportedException` | Spring MVC | `405` | `METHOD_NOT_ALLOWED` | Wrong HTTP method on endpoint |
| `RateLimitExceededException` | `RateLimitFilter`, `AuthController` | `429` | `RATE_LIMIT_EXCEEDED` | Too many requests from client IP |
| `BadCredentialsException` | `AuthServiceImpl` | `401` | `AUTHENTICATION_FAILED` | Wrong username or password |
| `DisabledException` | `JwtAuthenticationFilter` | `403` | `ACCOUNT_DISABLED` | Email not verified |
| `AccountSuspendedException` | `OrganizationService` | `403` | `ACCOUNT_SUSPENDED` | Organization suspended by admin |

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

## 6. Verification Levels

When documenting or reviewing implementation details, use these labels to distinguish confidence levels:

| Label | Meaning |
| :--- | :--- |
| ✅ **Verified** | Confirmed by reading the actual source code |
| 🔍 **Observed** | Seen in code but not exercised (e.g., config exists but untested in production) |
| ⚠️ **Needs Verification** | Inferred from naming conventions, comments, or partial code — requires further investigation |
| ❌ **Not Implemented** | Referenced in comments/docs but no implementation found in codebase |

---

## 7. Domain Glossary

| Term | Definition |
| :--- | :--- |
| **Crew** | A flat, peer-to-peer collaboration workspace without organizational hierarchy or manager sign-off. |
| **Organization** | A multi-tenant enterprise vault boundary governing custom RBAC roles, teams, OKRs/Goals, and HR leave requests. |
| **TaskMode** | Enum (`PERSONAL`, `CREW`, `ORG`) defining which lifecycle strategy and business rules govern a task. |
| **Role Priority** | An integer rank (0–100) assigned to an organization `Role`. Lower values = higher authority. Enforces that lower-priority users cannot assign or manage tasks for higher-priority users. |
| **TaskEvidence** | File, link, or text proof submitted by an assignee when moving an Organization task to `SUBMITTED` status. |
| **TeamObserver** | A read-only auditor role assigned to a department `Team` allowing task and activity feed inspection without mutation rights. |
| **Goal / OKR** | An Organization-scoped strategic objective container containing measurable key results. |
| **Token Version** | An integer field on `User` that is incremented to mass-invalidate all outstanding JWT access tokens. |
| **Correlation ID** | A UUID propagated via `X-Correlation-Id` header and MDC context for end-to-end request tracing across async boundaries. |
| **Project Bridge** | The mechanism allowing personal projects to be shared with Crews. Enterprise projects are explicitly blocked from this bridge. |
| **Optimistic Lock** | `@Version` field on `Task`, `Project`, `ChecklistItem` — prevents silent overwrites on concurrent edits. |
| **Announcement** | Organization-wide broadcast message created by admins/managers, displayed to all org members. |
| **CallerRunsPolicy** | Async thread pool backpressure strategy: when queue is full, the task executes on the caller's thread instead of being rejected. |
| **DataSeeder** | `CommandLineRunner` that bootstraps all `PermissionType` enum values into the `permissions` table on application startup (idempotent). |
| **MdcTaskDecorator** | Spring `TaskDecorator` that copies SLF4J MDC context from the calling thread to async worker threads. |
