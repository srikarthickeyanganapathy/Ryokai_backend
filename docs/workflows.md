# Workflows & Sequence Diagrams

Back to **[Master Index](README.md)**

---

### Diagram 1: User Registration & Authentication Flow

```mermaid
sequenceDiagram
    actor Client as SPA / Frontend
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthServiceImpl
    participant UserRepo as UserRepository
    participant Encoder as BCryptPasswordEncoder
    participant JwtProv as JwtTokenProvider
    participant RefRepo as RefreshTokenRepository

    Client->>AuthCtrl: POST /api/auth/register (RegisterRequestDTO)
    AuthCtrl->>AuthSvc: register(dto)
    AuthSvc->>UserRepo: existsByUsername / existsByEmail
    AuthSvc->>Encoder: encode(password)
    AuthSvc->>UserRepo: save(User)
    AuthSvc-->>Client: 201 Created ("Registration successful")

    Client->>AuthCtrl: POST /api/auth/login (LoginRequestDTO)
    AuthCtrl->>AuthSvc: authenticate(username, password)
    AuthSvc->>UserRepo: findByUsername
    AuthSvc->>Encoder: matches(rawPassword, encodedPassword)
    AuthSvc->>JwtProv: generateAccessToken(user)
    AuthSvc->>RefRepo: save(RefreshToken)
    AuthSvc-->>Client: 200 OK { accessToken, refreshToken }
```

---

### Diagram 2: JWT Refresh Token Rotation

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

---

### Diagram 3: Enterprise Task Assignment & Hierarchy Validation

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

---

### Diagram 4: Task Evidence Upload & Submission

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

---

### Diagram 5: Manager Approval & Rejection Flow

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

---

### Diagram 6: Assignor Task Recall Sequence

```mermaid
sequenceDiagram
    actor Assignor as Org Assignor
    participant StateCtrl as TaskStateController
    participant StateSvc as TaskStateTransitionServiceImpl
    participant TaskRepo as TaskRepository
    participant AuditSvc as TaskAuditService

    Assignor->>StateCtrl: POST /api/tasks/42/recall
    StateCtrl->>StateSvc: recallTask(42, Assignor)
    StateSvc->>TaskRepo: findById(42)
    
    alt Task Status != SUBMITTED
        StateSvc-->>Assignor: 400 Bad Request ("Only submitted tasks can be recalled")
    else Task Status == SUBMITTED
        StateSvc->>TaskRepo: save(Task.status = IN_PROGRESS)
        StateSvc->>AuditSvc: logStatusChange(42, SUBMITTED, IN_PROGRESS, Assignor)
        StateSvc-->>Assignor: 200 OK (Task Response DTO)
    end
```

---

### Diagram 7: Crew Real-Time Whiteboard Drawing Flow

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

### Diagram 8: Project Connection Bridge (Sharing & Revocation)

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

---

### Diagram 9: HR Leave Request & Task Reassignment

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
