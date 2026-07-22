# Complete System Workflows & Sequence Diagrams

Back to **[Master Index](README.md)**

This document details **every business workflow** implemented across the 32 controllers in the backend, grouped by domain module.

---

## 1. Authentication & Identity Workflows

### Workflow 1.1: User Registration
- **APIs**: `POST /api/auth/register` ([AuthController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/AuthController.java))
- **Service**: `AuthServiceImpl.register`
- **Execution Flow**:
  1. Client sends `RegisterRequestDTO` containing `username`, `email`, `password`.
  2. Verifies username and email uniqueness in `UserRepository`.
  3. Hashes password using `BCryptPasswordEncoder`.
  4. Saves new `User` entity to database (`superAdmin = false`).
  5. Sends email verification token via `EmailService`.

### Workflow 1.2: User Login & JWT Issuance
- **APIs**: `POST /api/auth/login` ([AuthController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/AuthController.java))
- **Diagram 1**: User Registration & Authentication Flow
```mermaid
sequenceDiagram
    actor Client as SPA / Frontend
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant UserRepo as UserRepository
    participant Encoder as BCryptPasswordEncoder
    participant JwtProv as JwtTokenProvider
    participant RefRepo as RefreshTokenRepository

    Client->>AuthCtrl: POST /api/auth/login (LoginRequestDTO)
    AuthCtrl->>AuthSvc: authenticate(username, password)
    AuthSvc->>UserRepo: findByUsername
    AuthSvc->>Encoder: matches(rawPassword, encodedPassword)
    AuthSvc->>JwtProv: generateAccessToken(user)
    AuthSvc->>RefRepo: save(RefreshToken: UUID, exp=7 days)
    AuthSvc-->>Client: 200 OK { accessToken, refreshToken }
```

### Workflow 1.3: JWT Refresh Token Rotation
- **APIs**: `POST /api/session/refresh` ([SessionController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/SessionController.java))
- **Diagram 2**: JWT Refresh Token Rotation
```mermaid
sequenceDiagram
    actor Client as SPA / Frontend
    participant AuthCtrl as SessionController
    participant RefSvc as RefreshTokenService
    participant JwtProv as JwtTokenProvider
    participant DB as Database

    Client->>AuthCtrl: POST /api/session/refresh (refreshToken)
    AuthCtrl->>RefSvc: verifyAndRotate(refreshToken)
    RefSvc->>DB: Validate Token (Not Expired, Not Revoked)
    RefSvc->>RefSvc: Revoke Current Refresh Token & Generate New UUID Token
    RefSvc->>DB: Save New Refresh Token
    RefSvc->>JwtProv: generateAccessToken(user)
    RefSvc-->>Client: 200 OK { accessToken, newRefreshToken }
```

### Workflow 1.4: Password Reset Pipeline
- **APIs**: `POST /api/auth/forgot-password` & `POST /api/auth/reset-password` ([PasswordResetController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/PasswordResetController.java))
- **Execution**: User inputs email -> System generates `PasswordResetToken` (1-hour expiration) and emails reset link -> User presents token + new password -> Password updated with BCrypt hash -> Reset token revoked.

### Workflow 1.5: Session Logout & Invalidation
- **APIs**: `POST /api/session/logout` & `POST /api/session/logout-all` ([SessionController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/SessionController.java))
- **Execution**: `logout` revokes current refresh token and adds current access JWT to `TokenDenylistService`. `logout-all` revokes all refresh tokens issued to the user across all devices.

---

## 2. Organization & Enterprise Vault Workflows

### Workflow 2.1: Provisioning Corporate Organization & Custom RBAC
- **APIs**: `POST /api/organizations` & `POST /api/organizations/{id}/roles` ([OrganizationController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/OrganizationController.java), [OrganizationRoleController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/OrganizationRoleController.java))
- **Execution**: Admin creates Organization -> Owner membership assigned -> Admin creates custom roles specifying integer `priority` (e.g. Director=90, Manager=50, Lead=30, Member=10) -> Permissions assigned via `PUT /api/organizations/{id}/roles/{roleId}/permissions`.

### Workflow 2.2: Department Team Structuring & Observer Oversight
- **APIs**: `POST /api/organizations/{id}/teams` & `POST /api/organizations/teams/{teamId}/observers` ([OrganizationTeamController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/OrganizationTeamController.java))
- **Execution**: Admin creates team under Organization -> Members added via `POST /teams/{teamId}/members` -> Read-only `TeamObserver`s assigned via `POST /teams/{teamId}/observers` for auditor/management visibility without mutation permissions.

### Workflow 2.3: In-App & Link Invitations
- **APIs**: `POST /api/organizations/{orgId}/invites` & `POST /api/organizations/{orgId}/invites/link` ([OrganizationInviteController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/OrganizationInviteController.java))
- **Execution**: Admin generates in-app invite for username or shareable link token -> Invitee accepts via `POST /api/invites/{inviteId}/accept` or `POST /api/invites/token/{token}/accept` -> Invitee assigned specified role in `organization_memberships`.

### Workflow 2.4: HR Leave Request & Active Task Reassignment
- **APIs**: `POST /api/organizations/{id}/leave` & `POST /api/organizations/{id}/leave/{requestId}/approve` ([OrganizationMembershipController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/OrganizationMembershipController.java))
- **Diagram 9**: HR Leave Request & Task Reassignment
```mermaid
sequenceDiagram
    actor Employee as Employee
    actor Manager as HR Manager
    participant HRCtrl as OrganizationMembershipController
    participant LeaveSvc as OrganizationLeaveService
    participant TaskRepo as TaskRepository
    participant DB as Database

    Employee->>HRCtrl: POST /api/organizations/{orgId}/leave (LeaveReasonDTO)
    HRCtrl->>LeaveSvc: requestLeave(orgId, Employee, reason)
    LeaveSvc->>DB: INSERT INTO leave_requests (status='PENDING')
    HRCtrl-->>Employee: 201 Created (LeaveRequestDTO)

    Manager->>HRCtrl: POST /api/organizations/{orgId}/leave/{requestId}/approve
    HRCtrl->>LeaveSvc: approveLeave(orgId, requestId, Manager)
    LeaveSvc->>DB: UPDATE leave_requests SET status = 'APPROVED'
    LeaveSvc->>TaskRepo: findByAssignee(Employee)
    LeaveSvc->>DB: Reassign active tasks or unassign pending tasks
    HRCtrl-->>Manager: 200 OK (LeaveRequestDTO Approved)
```

### Workflow 2.5: Admin Force-Leave / Dissolution
- **APIs**: `POST /api/organizations/{id}/admin-leave` ([OrganizationMembershipController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/OrganizationMembershipController.java))
- **Execution**: Owner chooses successor user ID and specifies whether to transfer ownership or dissolve org -> `OrganizationLifecycleService.leaveOrDissolve` validates no active non-terminal tasks remain -> Updates owner or soft-deletes organization.

---

## 3. Crew & Collaboration Workflows

### Workflow 3.1: Crew Discovery, Join & Ownership Transfer
- **APIs**: `POST /api/crews`, `GET /api/crews/discover`, `POST /api/crews/{crewId}/join`, `PUT /api/crews/{crewId}/transfer-ownership/{newOwnerId}` ([CrewController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/CrewController.java))
- **Execution**: Users create crew or discover public crews (`visibility = PUBLIC`) -> Authenticated user joins directly -> Creator can transfer ownership to any crew member.

### Workflow 3.2: Channel Messaging & Chat-to-Task Conversion
- **APIs**: `POST /api/crews/{crewId}/channels/{channelId}/messages` & `POST .../convert-to-task` ([CrewController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/CrewController.java))
- **Diagram 10**: Convert Chat Message to Task Flow
```mermaid
sequenceDiagram
    actor Member as Crew Member
    participant CrewCtrl as CrewController
    participant ChannelSvc as CrewChannelService
    participant TaskAssignSvc as TaskAssignmentServiceImpl
    participant DB as Database

    Member->>CrewCtrl: POST /api/crews/1/channels/2/messages/10/convert-to-task (ConvertToTaskRequestDTO)
    CrewCtrl->>ChannelSvc: convertMessageToTask(crewId, channelId, messageId, Member, dto)
    ChannelSvc->>DB: Fetch CrewMessage(id=10)
    ChannelSvc->>TaskAssignSvc: assignTask(Crew Task Command with message content)
    TaskAssignSvc->>DB: INSERT INTO tasks (title, description, crew_id=1, mode='CREW', status='TODO')
    ChannelSvc-->>Member: 200 OK (TaskResponseDTO)
```

### Workflow 3.3: Interactive STOMP Whiteboard Session
- **APIs**: `POST /api/crews/{crewId}/whiteboards` & `@MessageMapping("/whiteboards/{boardId}/draw")` ([WhiteboardController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/WhiteboardController.java), [WhiteboardSocketController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/WhiteboardSocketController.java))
- **Diagram 11**: Crew Real-Time Whiteboard Drawing Flow
```mermaid
sequenceDiagram
    actor ClientA as Crew Member A
    actor ClientB as Crew Member B
    participant REST as WhiteboardController (REST)
    participant WS as WhiteboardSocketController (STOMP)
    participant Broadcaster as RealtimeBroadcaster
    participant DB as Database

    ClientA->>REST: POST /api/crews/{crewId}/whiteboards (Title: "Architecture")
    REST->>DB: Save Whiteboard Entity
    REST-->>ClientA: 201 Created (boardId)

    ClientA->>WS: Connect STOMP -> Subscribe /topic/whiteboards/{boardId}
    ClientB->>WS: Connect STOMP -> Subscribe /topic/whiteboards/{boardId}

    rect rgba(128, 128, 128, 0.1)
        Note over ClientA, ClientB: Real-time Live Draw Stroke Loop
        ClientA->>WS: Send stroke payload to /whiteboards/{boardId}/draw
        WS->>WS: Validate Crew Membership (canDraw)
        WS->>Broadcaster: broadcastStroke(boardId, strokePayload)
        Broadcaster-->>ClientB: Push stroke data via /topic/whiteboards/{boardId}
    end

    ClientA->>REST: PUT /api/crews/{crewId}/whiteboards/{boardId}/snapshot (Base64 URL)
    REST->>DB: Update Whiteboard snapshot_url
    REST-->>ClientA: 200 OK (Snapshot Saved)
```

---

## 4. Task System Workflows

### Workflow 4.1: Task Assignment & Hierarchy Validation
- **APIs**: `POST /api/tasks/assign`, `POST /api/tasks/personal`, `POST /api/tasks/crew` ([TaskAssignmentController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskAssignmentController.java))
- **Diagram 3**: Enterprise Task Assignment & Hierarchy Validation
```mermaid
sequenceDiagram
    actor Assignor as Manager (Assignor)
    participant TaskCtrl as TaskAssignmentController
    participant TaskAssignSvc as TaskAssignmentServiceImpl
    participant HierVal as TaskHierarchyValidator
    participant TaskRepo as TaskRepository
    participant DB as Database

    Assignor->>TaskCtrl: POST /api/tasks/assign (TaskRequestDTO)
    TaskCtrl->>TaskAssignSvc: assignTask(TaskAssignmentCommand)
    TaskAssignSvc->>HierVal: validateOrgOrTeamTask(assignor, assignee, teamId)
    HierVal->>DB: Query Assignor Priority vs Assignee Priority
    
    alt Assignor Priority < Assignee Priority
        HierVal-->>TaskAssignSvc: Throw UnauthorizedActionException("Role priority violation")
        TaskAssignSvc-->>Assignor: 403 Forbidden
    else Assignor Priority >= Assignee Priority
        HierVal-->>TaskAssignSvc: Validation Succeeded
        TaskAssignSvc->>TaskRepo: save(Task: status=IN_PROGRESS, mode=ORG)
        TaskRepo->>DB: INSERT INTO tasks
        TaskAssignSvc-->>Assignor: 201 Created (TaskResponseDTO)
    end
```

### Workflow 4.2: Bulk Task Assignment
- **APIs**: `POST /api/tasks/bulk-assign` ([TaskAssignmentController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskAssignmentController.java))
- **Execution**: Assignor supplies list of `assigneeUsernames` (`BulkAssignRequestDTO`) -> `TaskBulkAssignmentService` iterates through list, executing individual `TaskHierarchyValidator` checks -> Creates individual tasks -> Returns `BulkAssignResponseDTO` listing successful assignments and skipped users.

### Workflow 4.3: Crew Task Claiming
- **APIs**: `POST /api/tasks/{taskId}/claim` ([TaskStateController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskStateController.java))
- **Diagram 9**: Crew Task Claiming Flow
```mermaid
sequenceDiagram
    actor Member as Crew Member
    participant StateCtrl as TaskStateController
    participant StateSvc as TaskStateTransitionServiceImpl
    participant CrewStrat as CrewTaskStrategy
    participant DB as Database

    Member->>StateCtrl: POST /api/tasks/42/claim
    StateCtrl->>StateSvc: claimTask(42, Member)
    StateSvc->>CrewStrat: canClaim(Member, Task)
    
    alt Member Not in Crew
        CrewStrat-->>StateSvc: Throw UnauthorizedActionException
        StateSvc-->>Member: 403 Forbidden
    else Task Already Claimed (assignee != null)
        StateSvc-->>Member: 400 Bad Request ("Task already claimed")
    else Valid Unclaimed Task
        StateSvc->>DB: UPDATE tasks SET assignee_id = Member.id, status = 'IN_PROGRESS'
        StateSvc-->>Member: 200 OK (TaskResponseDTO)
    end
```

### Workflow 4.4: Task Evidence Upload & Org Submission
- **APIs**: `POST /api/tasks/{taskId}/evidence` & `POST /api/tasks/{taskId}/submit` ([TaskEvidenceController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskEvidenceController.java), [TaskStateController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskStateController.java))
- **Diagram 4**: Task Evidence Upload & Submission
```mermaid
sequenceDiagram
    actor Assignee as Employee (Assignee)
    participant EvidCtrl as TaskEvidenceController
    participant EvidSvc as TaskEvidenceService
    participant StateCtrl as TaskStateController
    participant StateSvc as TaskStateTransitionServiceImpl
    participant OrgStrat as OrgTaskStrategy
    participant DB as Database

    Assignee->>EvidCtrl: POST /api/tasks/42/evidence (File / Link)
    EvidCtrl->>EvidSvc: addEvidence(42, assignee, dto)
    EvidSvc->>DB: INSERT INTO task_evidence (task_id=42, deleted=false)
    EvidCtrl-->>Assignee: 201 Created (EvidenceDTO)

    Assignee->>StateCtrl: POST /api/tasks/42/submit
    StateCtrl->>StateSvc: submitTask(42, assignee)
    StateSvc->>OrgStrat: validateTransition(Task, SUBMITTED)
    OrgStrat->>DB: countByTaskIdAndDeletedFalse(42)
    DB-->>OrgStrat: Count > 0
    OrgStrat-->>StateSvc: Transition Validated
    StateSvc->>DB: UPDATE tasks SET status = 'SUBMITTED' WHERE id = 42
    StateSvc-->>Assignee: 200 OK (TaskResponseDTO)
```

### Workflow 4.5: Manager Task Approval
- **APIs**: `POST /api/tasks/{taskId}/approve` ([TaskStateController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskStateController.java))
- **Diagram 5**: Manager Task Approval Flow
```mermaid
sequenceDiagram
    actor Manager as Org Manager (Reviewer)
    participant StateCtrl as TaskStateController
    participant StateSvc as TaskStateTransitionServiceImpl
    participant OrgStrat as OrgTaskStrategy
    participant AuditSvc as TaskAuditService
    participant DB as Database

    Manager->>StateCtrl: POST /api/tasks/42/approve
    StateCtrl->>StateSvc: approveTask(42, Manager)
    StateSvc->>OrgStrat: canApprove(Task, Manager)
    
    alt Manager is Assignee
        OrgStrat-->>StateSvc: Throw UnauthorizedActionException("No self-approval")
        StateSvc-->>Manager: 403 Forbidden
    else Manager is Authorized Reviewer
        OrgStrat-->>StateSvc: Approval Permitted
        StateSvc->>DB: UPDATE tasks SET status = 'APPROVED', approved_by_id = Manager.id
        StateSvc->>AuditSvc: logStatusChange(42, SUBMITTED, APPROVED, Manager)
        StateSvc-->>Manager: 200 OK (Task Approved)
    end
```

### Workflow 4.6: Manager Task Rejection
- **APIs**: `POST /api/tasks/{taskId}/reject` ([TaskStateController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskStateController.java))
- **Diagram 6**: Manager Task Rejection Flow
```mermaid
sequenceDiagram
    actor Manager as Org Manager (Reviewer)
    actor Assignee as Former Assignee
    participant StateCtrl as TaskStateController
    participant StateSvc as TaskStateTransitionServiceImpl
    participant OrgStrat as OrgTaskStrategy
    participant AuditSvc as TaskAuditService
    participant NotifSvc as TaskNotificationService
    participant DB as Database

    Manager->>StateCtrl: POST /api/tasks/42/reject (RejectReasonDTO)
    StateCtrl->>StateSvc: rejectTask(42, Manager, reason)
    StateSvc->>OrgStrat: canReject(Task, Manager)
    
    alt Task Status != SUBMITTED
        StateSvc-->>Manager: 400 Bad Request ("Only SUBMITTED tasks can be rejected")
    else Task Status == SUBMITTED
        StateSvc->>DB: UPDATE tasks SET status = 'REJECTED', rejection_reason = reason, assignee_id = null
        StateSvc->>AuditSvc: recordStatus(42, SUBMITTED -> REJECTED, reason)
        StateSvc->>NotifSvc: notifyTaskRejected(formerAssignee, reason)
        StateSvc-->>Manager: 200 OK (Task Response DTO)
    end
```

### Workflow 4.7: Assignee Task Recall
- **APIs**: `POST /api/tasks/{taskId}/recall` ([TaskStateController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskStateController.java))
- **Diagram 7**: Assignee Task Recall Sequence
```mermaid
sequenceDiagram
    actor Assignee as Employee (Assignee)
    participant StateCtrl as TaskStateController
    participant StateSvc as TaskStateTransitionServiceImpl
    participant TaskRepo as TaskRepository
    participant AuditSvc as TaskAuditService

    Assignee->>StateCtrl: POST /api/tasks/42/recall
    StateCtrl->>StateSvc: recallTask(42, Assignee)
    StateSvc->>TaskRepo: findById(42)
    
    alt User is Not Assignee
        StateSvc-->>Assignee: 403 Forbidden ("Only the assignee can recall a submitted task.")
    else Task Status != SUBMITTED
        StateSvc-->>Assignee: 400 Bad Request ("Only SUBMITTED tasks can be recalled.")
    else Authorized & Submitted
        StateSvc->>TaskRepo: save(Task.status = IN_PROGRESS)
        StateSvc->>AuditSvc: logStatusChange(42, SUBMITTED, IN_PROGRESS, Assignee)
        StateSvc-->>Assignee: 200 OK (Task Response DTO)
    end
```

### Workflow 4.8: Task Reassignment
- **APIs**: `PUT /api/tasks/{taskId}/reassign` ([TaskAssignmentController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskAssignmentController.java))
- **Diagram 8**: Task Reassignment Flow
```mermaid
sequenceDiagram
    actor Reassigner as Manager / Assignor
    actor NewAssignee as New Assignee
    participant TaskAssignCtrl as TaskAssignmentController
    participant LifeSvc as TaskLifecycleService
    participant MemRepo as OrganizationMembershipRepository
    participant TaskRepo as TaskRepository
    participant DB as Database

    Reassigner->>TaskAssignCtrl: PUT /api/tasks/42/reassign (TaskReassignRequestDTO)
    TaskAssignCtrl->>LifeSvc: reassignTask(42, newAssignee, Reassigner)
    
    alt Task is APPROVED or COMPLETED
        LifeSvc-->>Reassigner: 400 Bad Request ("Terminal tasks cannot be reassigned")
    else Task is Active
        LifeSvc->>MemRepo: findByUserAndOrganization(Reassigner & NewAssignee)
        alt Reassigner Role Priority < NewAssignee Role Priority
            LifeSvc-->>Reassigner: 403 Forbidden ("Cannot assign to someone with higher role priority")
        else Priority Validated
            LifeSvc->>TaskRepo: save(Task.assignee = NewAssignee)
            TaskRepo->>DB: UPDATE tasks SET assignee_id = NewAssignee.id
            LifeSvc-->>Reassigner: 200 OK (TaskResponseDTO)
        end
    end
```

### Workflow 4.9: Task Checklist Sub-task Management
- **APIs**: `POST /api/tasks/{taskId}/checklists` & `POST .../{itemId}/toggle` ([TaskChecklistController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskChecklistController.java))
- **Execution**: Assignee adds checklist items -> Toggles completion status (`completed = !completed`) -> ChecklistService recalculates progress percentage.

### Workflow 4.10: Task Dependency Creation & Resolution
- **APIs**: `POST /api/tasks/{taskId}/dependencies` ([TaskDependencyController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/TaskDependencyController.java))
- **Execution**: User links prerequisite task (`dependsOnTaskId`) -> `TaskDependencyService` validates scope equality -> When prerequisite task moves to `APPROVED`/`COMPLETED`, `TaskStateTransitionServiceImpl` emits `DEPENDENCY_RESOLVED` notification.

---

## 5. Project System Workflows

### Workflow 5.1: Personal Project Creation
- **APIs**: `POST /api/projects` ([ProjectController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/ProjectController.java))
- **Service**: `ProjectService.createProject`
- **Execution Flow**:
  1. User sends `ProjectRequestDTO` with `name`, `description`, `color`, `dueDate`.
  2. Sets `createdBy = currentUser`, `organization = null`, `team = null`.
  3. Saves `Project` entity to database.
  4. Returns `ProjectResponseDTO` with empty tasks list.

### Workflow 5.2: Enterprise Organization Project Creation
- **APIs**: `POST /api/projects` ([ProjectController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/ProjectController.java))
- **Execution Flow**:
  1. Manager sends `ProjectRequestDTO` specifying `organizationId` or `teamId`.
  2. Validates user is an authorized member of the organization.
  3. Sets `project.organization = org` and `project.team = team`.
  4. Sealed corporate project created—access governed by `ProjectPermissionHandler`.

### Workflow 5.3: Project Connection Bridge (Sharing Personal Project with Crew)
- **APIs**: `POST /api/projects/{projectId}/share/crew` ([ProjectController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/ProjectController.java)) & `POST /api/crews/{crewId}/projects/{projectId}` ([CrewController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/CrewController.java))
- **Service**: `ProjectService.shareProjectToCrew` & `CrewProjectService.shareProject`
- **Diagram 12**: Project Connection Bridge (Sharing & Revocation)
```mermaid
flowchart TD
    Start["User attempts to share Project with Crew<br/><code>POST /api/projects/{id}/share/crew</code>"] --> FetchProj["Fetch Project from Database"]
    FetchProj --> CheckOrg{"Is project.organization != null?"}
    
    CheckOrg -- "Yes (Enterprise Project)" --> Block["Throw AccessDeniedException<br/>'Enterprise projects cannot be shared with Crews'"] --> Res403["Return 403 Forbidden"]
    
    CheckOrg -- "No (Personal Project)" --> CheckOwner{"Is currentUser == project.creator?"}
    
    CheckOwner -- "No" --> ResDeny["Return 403 Forbidden ('Only owner can share')"]
    CheckOwner -- "Yes" --> PermitShare["Update project.crew = crewId<br/>Add crew members to project.collaborators"] --> Res200["Return 200 OK (Project Shared)"]

    Unshare["User unshares Project<br/><code>DELETE /api/projects/{id}/share/crew</code>"] --> DetachTasks["TaskRepository.detachProjectFromTasks(projectId)<br/>Set task.project_id = null"] --> ClearCrew["Set project.crew = null"] --> DoneUnshare["200 OK (Project Unshared)"]
```

### Workflow 5.4: Unsharing Project from Crew (Revocation)
- **APIs**: `DELETE /api/projects/{projectId}/share/crew` ([ProjectController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/ProjectController.java)) & `DELETE /api/crews/{crewId}/projects/{projectId}` ([CrewController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/CrewController.java))
- **Execution**: Only project creator or crew admin can unshare -> Removes crew from `project.sharedCrews` -> Executes `taskRepository.detachProjectFromTasks(projectId)` to un-link crew tasks from the project -> Returns updated `ProjectResponseDTO`.

### Workflow 5.5: Project Modification & Collaborator Management
- **APIs**: `PUT /api/projects/{projectId}` ([ProjectController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/ProjectController.java))
- **Execution**: `@PreAuthorize("hasPermission(#projectId, 'Project', 'EDIT')")` evaluates ownership -> Updates title, description, color, or adds explicit collaborator user IDs (`project.collaborators`).

### Workflow 5.6: Project Deletion & Soft Task Detachment
- **APIs**: `DELETE /api/projects/{projectId}` ([ProjectController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/ProjectController.java))
- **Execution**: `@PreAuthorize("hasPermission(#projectId, 'Project', 'DELETE')")` -> Detaches `project_id` on associated tasks (`set project_id = null`) -> Removes project entity from database.

---

## 6. Personal Workspace Workflows

### Workflow 6.1: Private Notes, Pomodoro Timers & Calendar Scheduling
- **APIs**: `POST /api/notes`, `POST /api/focus/start`, `POST /api/focus/{id}/stop`, `POST /api/calendar-events` ([NoteController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/NoteController.java), [FocusSessionController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/FocusSessionController.java), [CalendarEventController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/CalendarEventController.java))
- **Execution**: User creates markdown note -> Starts focus session tied to optional task (`FocusSession.startedAt = NOW`) -> Stops session (`FocusSession.durationMinutes` computed) -> Schedules private calendar events.

### Workflow 6.2: Entity Bookmarking & Dashboard Analytics
- **APIs**: `POST /api/saved-items` & `GET /api/dashboard/stats` ([SavedItemController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/SavedItemController.java), [DashboardController.java](file:///c:/Users/SEC/OneDrive/Desktop/Project/Ryokai/Ryokai_backend/taskflow/src/main/java/com/example/taskflow/controller/DashboardController.java))
- **Execution**: User bookmarks task, note, or project -> Saved item displayed in quick access bar -> `DashboardService` aggregates counts evaluating `isTerminal()` status logic (`status == APPROVED` or `status == COMPLETED`).
