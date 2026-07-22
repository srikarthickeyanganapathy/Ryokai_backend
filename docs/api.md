# API Reference Catalogue

Back to **[Master Index](README.md)**

---

## Interactive OpenAPI / Swagger UI
- **Swagger UI Interactive Specification**: Available at `/swagger-ui/index.html` (e.g. `http://localhost:8080/swagger-ui/index.html`) when backend is running.
- **OpenAPI v3 JSON Payload**: Available at `/v3/api-docs`.

---

## Endpoint Inventory by Module

### 1. Authentication APIs (`src/main/java/com/example/taskflow/controller/AuthController.java`)
- `POST /api/auth/register` - Register user (`RegisterRequestDTO` -> `201 Created`). Public.
- `POST /api/auth/login` - Login (`LoginRequestDTO` -> `JwtResponseDTO`). Public.
- `POST /api/session/refresh` - Rotate refresh token (`RefreshTokenRequestDTO` -> `JwtResponseDTO`). Public.
- `POST /api/session/logout` - Revoke current refresh token (`204 No Content`). Authenticated.

### 2. Task Module APIs (`src/main/java/com/example/taskflow/controller/TaskController.java`)
- `POST /api/tasks/personal` - Create personal task (`TaskRequestDTO` -> `TaskResponseDTO`).
- `POST /api/tasks/crew?crewId={id}` - Create crew task (`TaskRequestDTO` -> `TaskResponseDTO`).
- `POST /api/tasks/assign` - Assign org task (`TaskRequestDTO` -> `TaskResponseDTO`).
- `POST /api/tasks/{taskId}/submit` - Submit task for review (`TaskResponseDTO`). `@PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")`.
- `POST /api/tasks/{taskId}/approve` - Approve task (`TaskResponseDTO`). `@PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")`.
- `POST /api/tasks/{taskId}/reject` - Reject task (`RejectReasonDTO` -> `TaskResponseDTO`). `@PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")`.
- `POST /api/tasks/{taskId}/recall` - Recall task (`TaskResponseDTO`). `@PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")`.
- `POST /api/tasks/{taskId}/claim` - Claim crew task (`TaskResponseDTO`).
- `POST /api/tasks/{taskId}/complete-crew` - Direct peer completion of crew task (`TaskResponseDTO`).

### 3. Organization & Governance APIs (`src/main/java/com/example/taskflow/controller/OrganizationController.java`)
- `POST /api/organizations` - Provision organization (`OrganizationRequestDTO` -> `OrganizationResponseDTO`).
- `POST /api/organizations/{id}/roles` - Create custom RBAC role with priority (`RoleCreateRequestDTO`).
- `POST /api/organizations/{id}/teams` - Create department team (`CreateTeamRequestDTO`).
- `POST /api/organizations/teams/{teamId}/observers` - Assign team observer (`TeamMemberRequestDTO`).
- `POST /api/organizations/{orgId}/goals` - Define corporate OKR (`GoalRequestDTO`).
- `GET /api/organizations/{orgId}/workload` - Compute member bandwidth and task load (`WorkloadResponseDTO`).
- `POST /api/organizations/{id}/leave` - File HR leave request (`LeaveReasonDTO` -> `LeaveRequestDTO`).
- `POST /api/organizations/{id}/leave/{requestId}/approve` - Approve HR leave request (`LeaveRequestDTO`).
- `POST /api/organizations/{id}/leave/{requestId}/reject` - Reject HR leave request (`LeaveRejectDTO`).

### 4. Crew & Collaboration APIs (`src/main/java/com/example/taskflow/controller/CrewController.java`)
- `POST /api/crews` - Create crew (`CrewRequestDTO`).
- `POST /api/crews/{crewId}/invite` - Invite member by email.
- `POST /api/crews/invites/{inviteId}/accept` - Accept crew invite via path variable ID.
- `POST /api/crews/{crewId}/channels` - Create text channel (`CrewChannelRequestDTO`).
- `POST /api/crews/{crewId}/whiteboards` - Create whiteboard canvas (`WhiteboardRequestDTO`).
- `PUT /api/crews/{crewId}/whiteboards/{boardId}/snapshot` - Save Base64 canvas snapshot (`SnapshotRequestDTO`).

### 5. Personal Workspace APIs (`src/main/java/com/example/taskflow/controller/NoteController.java`)
- `POST /api/notes` - Create private note (`NoteRequestDTO`).
- `POST /api/focus/start` & `POST /api/focus/{id}/stop` - Pomodoro session management.
- `POST /api/calendar-events` - Schedule personal event (`CalendarEventRequestDTO`).
- `POST /api/saved-items` - Bookmark task, note, or project (`SavedItemRequestDTO`).
- `GET /api/dashboard/stats` - Multi-scoped aggregate stats (`DashboardStatsDTO`).
