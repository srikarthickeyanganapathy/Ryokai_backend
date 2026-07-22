# Developer Guide & Domain Glossary

Back to **[Master Index](README.md)**

---

## 1. How to Add a New Task State Action (Step-by-Step)

1. **Enum**: Add the new status to `TaskStatus` in `src/main/java/com/example/taskflow/domain/TaskStatus.java`.
2. **Strategy Method**: Define transition rules in `TaskLifecycleStrategy` and implement in `OrgTaskStrategy` or `CrewTaskStrategy`.
3. **Service Layer**: Add the transition method to `TaskStateTransitionService` and implement in `TaskStateTransitionServiceImpl`.
4. **Controller Method**: Add `@PostMapping("/{taskId}/your-action")` in `src/main/java/com/example/taskflow/controller/TaskStateController.java` with `@PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")`.
5. **Audit Event**: Log the state change via `TaskAuditService.logStatusChange(...)`.

---

## 2. Testing Infrastructure Guide

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

## 3. Exception & Error Catalogue

| Exception Class | Thrown By Component | HTTP Code | Root Cause / Scenario | Remediation Guidance |
| :--- | :--- | :--- | :--- | :--- |
| `ResourceNotFoundException` | `TaskRepository`, `UserRepository` | `404 Not Found` | Requested entity ID does not exist in DB | Verify entity ID in path parameter |
| `UnauthorizedActionException` | `TaskHierarchyValidator`, `OrgTaskStrategy` | `403 Forbidden` | Assignor role priority < assignee priority or cross-org action | Ensure assignor has equal or higher role priority |
| `InvalidStateTransitionException` | `OrgTaskStrategy`, `TaskStateTransitionServiceImpl` | `400 Bad Request` | Submitting task without evidence or invalid state jump | Upload evidence via `/api/tasks/{id}/evidence` before submit |
| `AccessDeniedException` | `CustomPermissionEvaluator`, `ProjectService` | `403 Forbidden` | Attempting to share enterprise project with Crew | Enterprise projects cannot be shared outside Org |
| `MethodArgumentNotValidException` | Spring MVC Validation Layer | `400 Bad Request` | `@NotBlank`, `@Size`, or `@Min` DTO constraint violation | Inspect response body error details map |
| `IllegalArgumentException` | `PersonalTaskStrategy`, `TaskAssignmentServiceImpl` | `400 Bad Request` | Assigning personal task to another user | Set `assigneeUsername` to current authenticated user |

---

## 4. Domain Glossary

- **Crew**: A flat, peer-to-peer collaboration workspace without organizational hierarchy or manager sign-off.
- **Organization**: A multi-tenant enterprise vault boundary governing custom RBAC roles, teams, OKRs/Goals, and HR leave requests.
- **TaskMode**: Enum (`PERSONAL`, `CREW`, `ORG`) defining which lifecycle strategy and business rules govern a task.
- **Role Priority**: An integer rank (0–100) assigned to an organization `Role`. Enforces that lower-priority users cannot assign or manage tasks for higher-priority users.
- **TaskEvidence**: File, link, or text proof submitted by an assignee when moving an Organization task to `SUBMITTED` status.
- **TeamObserver**: A read-only auditor role assigned to a department `Team` allowing task and activity feed inspection without mutation rights.
- **Goal / OKR**: An Organization-scoped strategic objective container containing metric key results.
