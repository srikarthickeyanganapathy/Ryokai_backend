# Domain Model & Entity Catalogue

Back to **[Master Index](README.md)**

---

## 1. Tri-Modal Workspace Invariants

- **Personal Workspace Invariants**:
  - `org_id = null`, `crew_id = null`.
  - Assignable strictly to creator.
  - Lifecycle: `TODO -> IN_PROGRESS -> COMPLETED`.
  - Private notes, focus sessions, bookmarks, calendar events.
  - No personal Goals/OKRs (Goals belong to Organization mode only).
  - Personal tasks visible to creator AND members of crews with which the task's project is shared.

- **Crew Collaboration Invariants**:
  - `org_id = null`, `crew_id = {crewId}`.
  - Created unclaimed (`assignee = null`, status `TODO`).
  - Claimed via `POST /api/tasks/{id}/claim` — uses optimistic locking (`@Version`) to prevent double-claim race conditions.
  - Completed directly via `POST /api/tasks/{id}/complete-crew`.
  - No review/approval pipeline. Statuses: `TODO`, `IN_PROGRESS`, `COMPLETED`.
  - STOMP whiteboard drawing requires active crew membership.

- **Organization Vault Invariants**:
  - `org_id = {orgId}`. Sealed corporate vault boundary.
  - Assignor priority must be `>=` assignee priority (`TaskHierarchyValidator`).
  - Review chain: `TODO -> IN_PROGRESS -> SUBMITTED -> APPROVED / REJECTED`.
  - Assignee self-approval is strictly forbidden.
  - Enterprise projects (`project.organization != null`) cannot be shared with Crews.

---

## 2. Graphical Entity Relationship Diagram

```mermaid
erDiagram
    users ||--o{ organization_memberships : "has membership"
    users ||--o{ crew_members : "participates"
    users ||--o{ tasks : "creates / assigned"
    users ||--o{ refresh_tokens : "authenticates"
    users ||--o{ notifications : "receives"
    users ||--o{ notes : "owns"
    users ||--o{ calendar_events : "schedules"
    users ||--o{ focus_sessions : "tracks"
    users ||--o{ saved_items : "bookmarks"
    users }o--o{ roles : "global roles (M:N)"
    
    organizations ||--o{ organization_memberships : "contains"
    organizations ||--o{ teams : "structures"
    organizations ||--o{ roles : "defines RBAC (org-scoped)"
    organizations ||--o{ goals : "tracks OKRs"
    organizations ||--o{ leave_requests : "governs HR"
    organizations ||--o{ announcements : "broadcasts"
    organizations ||--o{ organization_invites : "invitations"
    
    teams ||--o{ team_members : "members (M:N)"
    teams ||--o{ team_observers : "auditors"
    teams ||--o{ team_messages : "chat"
    
    crews ||--o{ crew_members : "has members"
    crews ||--o{ crew_channels : "channels"
    crews ||--o{ whiteboards : "whiteboards"
    crews ||--o{ crew_invites : "invitations"
    crews }o--o{ projects : "shares (M:N)"
    
    crew_channels ||--o{ crew_messages : "messages"
    
    projects ||--o{ tasks : "groups"
    
    tasks ||--o{ task_evidence : "requires proof"
    tasks ||--o{ task_dependencies : "prerequisites"
    tasks ||--o{ checklist_items : "sub-tasks"
    tasks ||--o{ task_comments : "comments"
    tasks ||--o{ task_status_history : "audit trail"
    tasks ||--o{ task_activity_logs : "activity feed"
    
    goals ||--o{ key_results : "measures"
    
    roles }o--o{ permissions : "grants (M:N)"
```

---

## 3. Entity Catalogue & Schema Blueprint

### Core Identity

#### `User` (`domain/User.java`)
- **Purpose**: Global identity across all modes.
- **Fields**: `id`, `username` (unique), `email` (unique), `password` (BCrypt-12), `isSuperAdmin` (boolean), `emailVerified` (boolean), `emailNotificationsEnabled` (boolean), `tokenVersion` (int, for mass JWT invalidation).
- **Relationships**: ManyToMany → `Role`, OneToMany → `OrganizationMembership`, `RefreshToken`, `Notification`, `Note`, `CalendarEvent`, `FocusSession`, `SavedItem`.

#### `Role` (`domain/Role.java`)
- **Purpose**: Custom RBAC role with integer priority rank, scoped to organization.
- **Fields**: `id`, `name`, `description`, `priority` (int, lower = higher authority), `organization_id` (FK, nullable for global roles).
- **Relationships**: ManyToMany → `Permission`, ManyToOne → `Organization`.

#### `Permission` (`domain/Permission.java`)
- **Purpose**: Granular permission tokens (19 types defined in `PermissionType` enum).
- **Fields**: `id`, `name` (unique), `description`.

### Authentication

#### `RefreshToken` (`domain/RefreshToken.java`)
- **Purpose**: Persistent refresh token for JWT rotation with replay detection.
- **Fields**: `id`, `tokenHash` (SHA-256 of raw token), `userId`, `deviceInfo` (User-Agent), `createdAt`, `expiresAt`, `replacedByTokenHash` (for rotation chain), `revoked` (boolean).

#### `PasswordResetToken` (`domain/PasswordResetToken.java`)
- **Purpose**: One-time-use password reset token.
- **Fields**: `id`, `token`, `userId`, `expiryDate` (1 hour), `used` (boolean).

### Organization Domain

#### `Organization` (`domain/Organization.java`)
- **Purpose**: Multi-tenant enterprise vault boundary.
- **Fields**: `id`, `name`, `description`, `slug` (unique), `suspended` (boolean), `deleted` (boolean, soft-delete).
- **Relationships**: OneToMany → `OrganizationMembership`, `Team`, `Goal`, `Announcement`, `LeaveRequest`, `OrganizationInvite`.

#### `OrganizationMembership` (`domain/OrganizationMembership.java`)
- **Purpose**: Join table linking users to organizations with a specific role.
- **Fields**: `id`, `joinedAt`.
- **Relationships**: ManyToOne → `User`, `Organization`, `Role` (orgRole, EAGER fetch).

#### `Team` (`domain/Team.java`)
- **Purpose**: Sub-group within an organization for team-based task scoping.
- **Fields**: `id`, `name`, `slug`.
- **Relationships**: ManyToOne → `Organization`, ManyToMany → `User` (members).

#### `TeamObserver` (`domain/TeamObserver.java`)
- **Purpose**: Read-only auditor role on a team. Vetoed from all write operations.
- **Key**: Composite (`teamId`, `userId`).

#### `TeamMessage` (`domain/TeamMessage.java`)
- **Purpose**: Chat messages within organization teams.
- **Fields**: `id`, `content`, `createdAt`.
- **Relationships**: ManyToOne → `Team`, `User` (author).

#### `OrganizationInvite` (`domain/OrganizationInvite.java`)
- **Purpose**: In-app invite or shareable link for org membership.
- **Fields**: `id`, `status` (PENDING/ACCEPTED/DECLINED/EXPIRED), `token` (UUID for link-based invites).
- **Relationships**: ManyToOne → `Organization`, `User` (inviter, invitee), `Role`.

#### `LeaveRequest` (`domain/LeaveRequest.java`)
- **Purpose**: HR leave request with approval workflow.
- **Fields**: `id`, `reason`, `status` (PENDING/APPROVED/REJECTED), `reviewerComment`.
- **Relationships**: ManyToOne → `Organization`, `User` (requester, reviewer).

#### `Announcement` (`domain/Announcement.java`)
- **Purpose**: Organization-wide broadcast messages.
- **Fields**: `id`, `title`, `content`, `pinned` (boolean).
- **Relationships**: ManyToOne → `Organization`, `User` (author).

#### `Goal` (`domain/Goal.java`)
- **Purpose**: Corporate OKR container scoped to Organization.
- **Fields**: `id`, `title`, `description`, `status` (`NOT_STARTED`, `IN_PROGRESS`, `AT_RISK`, `COMPLETED`), `startDate`, `endDate`.
- **Relationships**: ManyToOne → `Organization`, `User` (owner), OneToMany → `KeyResult`.

#### `KeyResult` (`domain/KeyResult.java`)
- **Purpose**: Measurable outcome linked to a Goal.
- **Fields**: `id`, `title`, `currentValue`, `targetValue`, `unit`.
- **Relationships**: ManyToOne → `Goal`.

### Task Domain

#### `Task` (`domain/Task.java`)
- **Purpose**: Dynamic multi-scoped task entity — the central business object.
- **Fields**: `id`, `title`, `description`, `currentStatus` (`TaskStatus` enum), `priority` (`TaskPriority` enum), `dueDate`, `tags`, `archived`, `personal`, `mode` (`TaskMode` enum: PERSONAL/ORG/CREW), `rejectionReason`, `version` (`@Version` — optimistic locking).
- **Relationships**: ManyToOne → `User` (creator, assignee, reviewer), `Organization`, `Team`, `Crew`, `Project`. OneToMany → `TaskComment`, `ChecklistItem`, `TaskEvidence`, `TaskDependency`, `TaskStatusHistory`, `TaskActivityLog`.

#### `TaskEvidence` (`domain/TaskEvidence.java`)
- **Purpose**: Proof submitted for task review. Supports file upload metadata and link-based evidence.
- **Fields**: `id`, `fileUrl`, `fileName`, `fileType`, `fileSizeBytes`, `evidenceType` (`FILE`/`LINK`/`TEXT`), `uploadedAt`, `deleted` (soft-delete), `deletedAt`, `deletedBy`.
- **Relationships**: ManyToOne → `Task`, `User` (uploader).

#### `TaskComment` (`domain/TaskComment.java`)
- **Purpose**: Threaded comments on tasks.
- **Fields**: `id`, `content`, `parentId` (self-referencing for threading), `createdAt`, `updatedAt`.
- **Relationships**: ManyToOne → `Task`, `User` (author).

#### `ChecklistItem` (`domain/ChecklistItem.java`)
- **Purpose**: Sub-task checklist items within a task.
- **Fields**: `id`, `content`, `done` (boolean), `displayOrder`, `version` (`@Version`).
- **Relationships**: ManyToOne → `Task`, `User` (createdBy).

#### `TaskDependency` (`domain/TaskDependency.java`)
- **Purpose**: Prerequisite relationships between tasks.
- **Fields**: `id`, `dependencyType` (`BLOCKS`/`BLOCKED_BY`).
- **Relationships**: ManyToOne → `Task` (task, dependsOn).

#### `TaskStatusHistory` (`domain/TaskStatusHistory.java`)
- **Purpose**: Immutable audit trail of task status transitions.
- **Fields**: `id`, `fromStatus`, `toStatus`, `changedAt`, `taskTitleSnapshot`, `actorUsernameSnapshot`.
- **Relationships**: ManyToOne → `Task`, `User` (changedBy).

### Crew Domain

#### `Crew` (`domain/Crew.java`)
- **Purpose**: Flat peer-to-peer collaboration group.
- **Fields**: `id`, `name`, `description`, `visibility` (`PRIVATE`/`PUBLIC_LINK`/`PUBLIC`), `maxMembers`.
- **Relationships**: ManyToOne → `User` (creator), OneToMany → `CrewMember`, `CrewChannel`, `CrewInvite`, ManyToMany → `Project` (sharedProjects).

#### `CrewMember` (`domain/CrewMember.java`)
- **Key**: Composite (`crewId`, `userId`).
- **Fields**: `role` (`OWNER`/`MEMBER`), `joinedAt`.

#### `CrewChannel` (`domain/CrewChannel.java`)
- **Fields**: `id`, `name`, `type` (`TEXT`/`VOICE`).
- **Relationships**: ManyToOne → `Crew`, OneToMany → `CrewMessage`.

#### `CrewMessage` (`domain/CrewMessage.java`)
- **Fields**: `id`, `content`, `editedAt` (null if never edited).
- **Relationships**: ManyToOne → `CrewChannel`, `User` (author), `Task` (optional linked task).

#### `CrewInvite` (`domain/CrewInvite.java`)
- **Fields**: `id` (UUID), `email`, `status` (`PENDING`/`ACCEPTED`/`EXPIRED`), `expiresAt`.
- **Relationships**: ManyToOne → `Crew`, `User` (inviter, invitee).

### Project Domain

#### `Project` (`domain/Project.java`)
- **Purpose**: Task grouping mechanism across all three task modes.
- **Fields**: `id`, `name`, `description`, `status` (`ACTIVE`/`COMPLETED`/`ARCHIVED`), `version` (`@Version`).
- **Relationships**: ManyToOne → `User` (owner), `Crew` (sharedCrew — legacy single-crew). ManyToMany → `Crew` (sharedCrews), `User` (collaborators).

#### `Whiteboard` (`domain/Whiteboard.java`)
- **Purpose**: Collaborative canvas for real-time drawing within a Crew.
- **Fields**: `id`, `name`, `dataUrl` (LONGTEXT Base64), `lastSnapshotAt`.
- **Relationships**: ManyToOne → `Crew`, `User` (creator).

### Productivity Domain

#### `FocusSession` (`domain/FocusSession.java`)
- **Fields**: `id`, `startTime`, `endTime`, `durationMinutes`, `mode` (`FOCUS`/`SHORT_BREAK`/`LONG_BREAK`).
- **Relationships**: ManyToOne → `User`, `Task` (optional).

#### `CalendarEvent` (`domain/CalendarEvent.java`)
- **Fields**: `id`, `title`, `description`, `startTime`, `endTime`, `allDay`.
- **Relationships**: ManyToOne → `User`.

#### `Note` (`domain/Note.java`)
- **Fields**: `id`, `title`, `content`.
- **Relationships**: ManyToOne → `User`.

#### `SavedItem` (`domain/SavedItem.java`)
- **Fields**: `id`, `savedAt`.
- **Relationships**: ManyToOne → `User`, `Task`.

### Notification Domain

#### `Notification` (`domain/Notification.java`)
- **Fields**: `id`, `type` (`NotificationEvent` enum — 24 event types), `title`, `message`, `taskId`, `taskTitleSnapshot`, `read` (boolean), `createdAt`, `deduplicationKey`.
- **Relationships**: ManyToOne → `User` (recipient), `User` (actor).

### Audit Domain

#### `AuditEvent` (`domain/AuditEvent.java`)
- **Fields**: `id`, `eventType`, `entityType`, `entityId`, `oldValueJson` (JSONB), `newValueJson` (JSONB), `reason`, `occurredAt`.
- **Relationships**: ManyToOne → `User` (actor).

#### `SecurityAuditEvent` (`domain/SecurityAuditEvent.java`)
- **Fields**: `id`, `eventType`, `ipAddress`, `deviceInfo`, `metadataJson` (JSONB), `occurredAt`, `success` (boolean).
- **Relationships**: ManyToOne → `User` (actor).

#### `TaskActivityLog` (`domain/TaskActivityLog.java`)
- **Fields**: `id`, `actionType`, `entityType`, `entityId`, `metadataJson` (JSONB), `source`, `ipAddress`, `userAgent`, `correlationId`.
- **Relationships**: ManyToOne → `Task`, `User` (actor).

#### `ProjectActivityLog` (`domain/ProjectActivityLog.java`)
- **Fields**: `id`, `actionType`, `entityType`, `entityId`, `metadataJson` (JSONB), `source`, `ipAddress`, `userAgent`, `correlationId`.
- **Relationships**: ManyToOne → `Project`, `User` (actor).

### Domain Events (Spring ApplicationEvents)

| Event Class | File | Published When |
| :--- | :--- | :--- |
| `TaskStatusChangedEvent` | `domain/events/task/TaskStatusChangedEvent.java` | Task status transitions |
| `EvidenceUploadedEvent` | `domain/events/task/EvidenceUploadedEvent.java` | Evidence added to a task |
| `NotificationCreatedEvent` | `notification/NotificationCreatedEvent.java` | Notification persisted — triggers WebSocket push |

---

## 4. Concurrency Control

Three entities use `@Version` for optimistic locking. Concurrent modifications cause `OptimisticLockingFailureException` → HTTP `409 Conflict` (code: `OPTIMISTIC_LOCK_CONFLICT`):

| Entity | Field | Reason |
| :--- | :--- | :--- |
| `Task` | `version` | Prevents concurrent status updates, edits |
| `Project` | `version` | Prevents concurrent metadata updates |
| `ChecklistItem` | `version` | Prevents concurrent checklist toggling |

The crew task claim flow explicitly catches `OptimisticLockingFailureException` and converts it to `IllegalStateException("Task already claimed")`.
