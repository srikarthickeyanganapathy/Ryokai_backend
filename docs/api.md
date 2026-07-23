# API Reference Catalogue

Back to **[Master Index](README.md)**

---

## Interactive OpenAPI / Swagger UI
- **Swagger UI Interactive Specification**: Available at `/swagger-ui/index.html` (e.g. `http://localhost:8080/swagger-ui/index.html`) when backend is running.
- **OpenAPI v3 JSON Payload**: Available at `/v3/api-docs`.

---

## Endpoint Inventory by Module (35 Controllers)

### 1. Authentication (`controller/AuthController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | `RegisterRequestDTO` → `201 Created` |
| `POST` | `/api/auth/login` | Public (rate limited: 10/15min per IP+user) | `LoginRequestDTO` → `JwtResponseDTO` |
| `POST` | `/api/auth/forgot-password` | Public (rate limited: 5/hr per IP) | `ForgotPasswordRequestDTO` → `200 OK` |

### 2. Session Management (`controller/SessionController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/session/refresh` | Public (rate limited: 30/15min) | `RefreshTokenRequestDTO` → `JwtResponseDTO` |
| `POST` | `/api/session/logout` | Authenticated (rate limited: 20/15min) | — → `204 No Content` |
| `POST` | `/api/session/logout-all` | Authenticated | — → `200 OK` (increments tokenVersion) |
| `GET` | `/api/session/verify-email` | Public (rate limited: 10/15min) | `?token=...` → `200 OK` |
| `POST` | `/api/session/resend-verification` | Authenticated (rate limited: 5/hr per IP+email) | — → `200 OK` |

### 3. Password Reset (`controller/PasswordResetController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/forgot-password` | Public | `ForgotPasswordRequestDTO` → `200 OK` |
| `POST` | `/api/auth/reset-password` | Public | `ResetPasswordRequestDTO` → `200 OK` |

### 4. User Profile (`controller/UserController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/users/me` | Authenticated | — → `UserResponseDTO` |
| `PUT` | `/api/users/me` | Authenticated | `UpdateProfileRequestDTO` → `UserResponseDTO` |
| `POST` | `/api/users/me/password` | Authenticated | `ChangePasswordRequestDTO` → `200 OK` |
| `GET` | `/api/users/me/sessions` | Authenticated | — → `List<SessionDTO>` |
| `DELETE` | `/api/users/me/sessions/{tokenId}` | Authenticated | — → `204 No Content` |
| `GET` | `/api/users` | Authenticated | — → `List<UserResponseDTO>` (org-scoped for members, all for SuperAdmin) |

### 5. Task CRUD (`controller/TaskController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tasks` | Authenticated | — → `List<TaskResponseDTO>` (paginated, filtered by mode/status/assignee) |
| `GET` | `/api/tasks/{taskId}` | `VIEW` | — → `TaskResponseDTO` |
| `PUT` | `/api/tasks/{taskId}` | `EDIT` | `TaskRequestDTO` → `TaskResponseDTO` |
| `DELETE` | `/api/tasks/{taskId}` | `DELETE` | — → `204 No Content` |
| `PUT` | `/api/tasks/{taskId}/archive` | `ARCHIVE` | — → `TaskResponseDTO` |

### 6. Task Assignment (`controller/TaskAssignmentController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/tasks/personal` | Authenticated | `TaskRequestDTO` → `TaskResponseDTO` (201) |
| `POST` | `/api/tasks/crew?crewId={id}` | Authenticated | `TaskRequestDTO` → `TaskResponseDTO` (201) |
| `POST` | `/api/tasks/assign` | Authenticated | `TaskRequestDTO` → `TaskResponseDTO` (201) |
| `POST` | `/api/tasks/bulk-assign` | Authenticated | `BulkAssignRequestDTO` → `BulkAssignResponseDTO` |
| `PUT` | `/api/tasks/{taskId}/reassign` | `EDIT` | `TaskReassignRequestDTO` → `TaskResponseDTO` |

### 7. Task State Transitions (`controller/TaskStateController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/tasks/{taskId}/submit` | `EDIT` | — → `TaskResponseDTO` |
| `POST` | `/api/tasks/{taskId}/approve` | `REVIEW` | — → `TaskResponseDTO` |
| `POST` | `/api/tasks/{taskId}/reject` | `REVIEW` | `RejectReasonDTO` → `TaskResponseDTO` |
| `POST` | `/api/tasks/{taskId}/recall` | `EDIT` | — → `TaskResponseDTO` |
| `POST` | `/api/tasks/{taskId}/claim` | Crew Member | — → `TaskResponseDTO` |
| `POST` | `/api/tasks/{taskId}/complete-crew` | `EDIT` | — → `TaskResponseDTO` |

### 8. Task Evidence (`controller/TaskEvidenceController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tasks/{taskId}/evidence` | `VIEW` | — → `List<TaskEvidenceDTO>` |
| `POST` | `/api/tasks/{taskId}/evidence` | `EDIT` | `TaskEvidenceRequestDTO` → `TaskEvidenceDTO` (201) |
| `DELETE` | `/api/tasks/{taskId}/evidence/{evidenceId}` | `EDIT` | — → `204 No Content` |

### 9. Task Checklists (`controller/TaskChecklistController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tasks/{taskId}/checklists` | `VIEW` | — → `List<ChecklistItemDTO>` |
| `POST` | `/api/tasks/{taskId}/checklists` | `EDIT` | `ChecklistItemRequestDTO` → `ChecklistItemDTO` (201) |
| `PUT` | `/api/tasks/{taskId}/checklists/{itemId}` | `EDIT` | `ChecklistItemRequestDTO` → `ChecklistItemDTO` |
| `POST` | `/api/tasks/{taskId}/checklists/{itemId}/toggle` | `EDIT` | — → `ChecklistItemDTO` |
| `DELETE` | `/api/tasks/{taskId}/checklists/{itemId}` | `EDIT` | — → `204 No Content` |

### 10. Task Comments (`controller/TaskCommentController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tasks/{taskId}/comments` | `VIEW` | — → `Page<TaskCommentDTO>` (max 100/page) |
| `POST` | `/api/tasks/{taskId}/comments` | `VIEW` | `CommentRequestDTO` (text, parentId) → `TaskCommentDTO` (201) |

### 11. Task Dependencies (`controller/TaskDependencyController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tasks/{taskId}/dependencies` | `VIEW` | — → `List<TaskDependencyDTO>` |
| `POST` | `/api/tasks/{taskId}/dependencies` | `DEPENDENCY_EDIT` | `TaskDependencyRequestDTO` → `TaskDependencyDTO` (201) |
| `DELETE` | `/api/tasks/{taskId}/dependencies/{depId}` | `DEPENDENCY_EDIT` | — → `204 No Content` |

### 12. Task Activity Logs (`controller/TaskActivityController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/tasks/{taskId}/activities` | Authenticated | — → `Page<TaskActivityLog>` |

### 13. Project Activity Logs (`controller/ProjectActivityController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/projects/{projectId}/activities` | Authenticated | — → `Page<ProjectActivityLog>` |

### 14. Projects (`controller/ProjectController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/projects` | Authenticated | `ProjectRequestDTO` → `ProjectResponseDTO` (201) |
| `GET` | `/api/projects` | Authenticated | — → `List<ProjectResponseDTO>` |
| `GET` | `/api/projects/{projectId}` | `VIEW` | — → `ProjectResponseDTO` |
| `PUT` | `/api/projects/{projectId}` | `EDIT` | `ProjectRequestDTO` → `ProjectResponseDTO` |
| `DELETE` | `/api/projects/{projectId}` | `DELETE` | — → `204 No Content` |
| `POST` | `/api/projects/{projectId}/share/crew` | Authenticated | `ShareProjectRequestDTO` → `ProjectResponseDTO` |
| `DELETE` | `/api/projects/{projectId}/share/crew` | Authenticated | — → `ProjectResponseDTO` |

### 15. Organization Management (`controller/OrganizationController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/organizations` | Authenticated | `OrganizationRequestDTO` → `OrganizationResponseDTO` (201) |
| `GET` | `/api/organizations/{orgId}` | Authenticated | — → `OrganizationResponseDTO` |
| `PUT` | `/api/organizations/{orgId}` | Authenticated | `OrganizationRequestDTO` → `OrganizationResponseDTO` |

### 16. Organization Roles (`controller/OrganizationRoleController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/organizations/{orgId}/roles` | `ROLE_MANAGE` | `RoleCreateRequestDTO` → `RoleResponseDTO` (201) |
| `GET` | `/api/organizations/{orgId}/roles` | Authenticated | — → `List<RoleResponseDTO>` |
| `PUT` | `/api/organizations/{orgId}/roles/{roleId}` | `ROLE_MANAGE` | `RoleUpdateRequestDTO` → `RoleResponseDTO` |
| `DELETE` | `/api/organizations/{orgId}/roles/{roleId}` | `ROLE_MANAGE` | — → `204 No Content` |
| `PUT` | `/api/organizations/{orgId}/roles/{roleId}/permissions` | `ROLE_MANAGE` | `List<PermissionType>` → `RoleResponseDTO` |

### 17. Organization Membership (`controller/OrganizationMembershipController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/organizations/{orgId}/members` | Authenticated | — → `List<MemberResponseDTO>` |
| `PUT` | `/api/organizations/{orgId}/members/{memberId}/role` | `ORG_MEMBER_REMOVE` | `UpdateRoleRequestDTO` → `200 OK` |
| `DELETE` | `/api/organizations/{orgId}/members/{memberId}` | `ORG_MEMBER_REMOVE` | — → `204 No Content` |
| `POST` | `/api/organizations/{orgId}/leave` | Authenticated | `LeaveReasonDTO` → `LeaveRequestDTO` (201) |
| `POST` | `/api/organizations/{orgId}/leave/{requestId}/approve` | `LEAVE_REQUEST_MANAGE` | — → `LeaveRequestDTO` |
| `POST` | `/api/organizations/{orgId}/leave/{requestId}/reject` | `LEAVE_REQUEST_MANAGE` | `LeaveRejectDTO` → `LeaveRequestDTO` |
| `POST` | `/api/organizations/{orgId}/admin-leave` | Org Owner | `AdminLeaveRequestDTO` → `200 OK` |

### 18. Organization Invites (`controller/OrganizationInviteController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/organizations/{orgId}/invites` | Authenticated | `InviteMemberRequestDTO` → `OrganizationInviteDTO` (201) |
| `POST` | `/api/organizations/{orgId}/invites/link` | Authenticated | `UpdateRoleRequestDTO` → `OrganizationInviteDTO` (201) |
| `GET` | `/api/invites` | Authenticated | — → `List<OrganizationInviteDTO>` |
| `POST` | `/api/invites/{inviteId}/accept` | Authenticated | — → `OrganizationInviteDTO` |
| `POST` | `/api/invites/{inviteId}/decline` | Authenticated | — → `OrganizationInviteDTO` |
| `POST` | `/api/invites/token/{token}/accept` | Authenticated | — → `OrganizationInviteDTO` |

### 19. Organization Teams (`controller/OrganizationTeamController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/organizations/{orgId}/teams` | Authenticated | `CreateTeamRequestDTO` → `TeamResponseDTO` (201) |
| `GET` | `/api/organizations/{orgId}/teams` | Authenticated | — → `List<TeamResponseDTO>` |
| `POST` | `/api/organizations/teams/{teamId}/members` | Authenticated | `TeamMemberRequestDTO` → `200 OK` |
| `DELETE` | `/api/organizations/teams/{teamId}/members/{userId}` | Authenticated | — → `204 No Content` |
| `POST` | `/api/organizations/teams/{teamId}/observers` | Authenticated | `TeamMemberRequestDTO` → `200 OK` |
| `DELETE` | `/api/organizations/teams/{teamId}/observers/{userId}` | Authenticated | — → `204 No Content` |

### 20. Team Messages (`controller/TeamMessageController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/teams/{teamId}/messages` | Authenticated (team member) | — → `List<TeamMessageResponseDTO>` |
| `POST` | `/api/teams/{teamId}/messages` | Authenticated (team member) | `TeamMessageCreateRequestDTO` → `TeamMessageResponseDTO` (201) |
| `DELETE` | `/api/teams/{teamId}/messages/{messageId}` | Authenticated (author/admin) | — → `204 No Content` |

### 21. Announcements (`controller/AnnouncementController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/organizations/{orgId}/announcements` | Authenticated (org member) | — → `Page<AnnouncementResponseDTO>` (20/page) |
| `POST` | `/api/organizations/{orgId}/announcements` | Authenticated (admin/mgr) | `AnnouncementRequestDTO` → `AnnouncementResponseDTO` (201) |
| `DELETE` | `/api/organizations/{orgId}/announcements/{id}` | Authenticated (admin/author) | — → `204 No Content` |

### 22. Goals & OKRs (`controller/GoalController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/organizations/{orgId}/goals` | `GOAL_MANAGE` | `GoalRequestDTO` → `GoalResponseDTO` (201) |
| `GET` | `/api/organizations/{orgId}/goals` | Authenticated | — → `List<GoalResponseDTO>` |
| `PUT` | `/api/organizations/{orgId}/goals/{goalId}` | `GOAL_MANAGE` | `GoalRequestDTO` → `GoalResponseDTO` |
| `DELETE` | `/api/organizations/{orgId}/goals/{goalId}` | `GOAL_MANAGE` | — → `204 No Content` |

### 23. Workload Analytics (`controller/WorkloadController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/organizations/{orgId}/workload` | Authenticated | — → `List<UserWorkloadDTO>` |

### 24. Crew Management (`controller/CrewController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/crews` | Authenticated | `CrewRequestDTO` → `CrewResponseDTO` (201) |
| `GET` | `/api/crews` | Authenticated | — → `List<CrewResponseDTO>` |
| `GET` | `/api/crews/discover` | Authenticated | — → `List<CrewResponseDTO>` (public crews) |
| `GET` | `/api/crews/{crewId}` | Authenticated | — → `CrewResponseDTO` |
| `POST` | `/api/crews/{crewId}/join` | Authenticated | — → `200 OK` |
| `POST` | `/api/crews/{crewId}/invite` | Crew Owner/Member | `CrewInviteRequestDTO` → `CrewInviteDTO` |
| `POST` | `/api/crews/invites/{inviteId}/accept` | Authenticated | — → `200 OK` |
| `POST` | `/api/crews/{crewId}/channels` | Crew Member | `CrewChannelRequestDTO` → `CrewChannelDTO` |
| `GET` | `/api/crews/{crewId}/channels` | Crew Member | — → `List<CrewChannelDTO>` |
| `POST` | `/api/crews/{crewId}/channels/{channelId}/messages` | Crew Member | `CrewMessageRequestDTO` → `CrewMessageDTO` |
| `GET` | `/api/crews/{crewId}/channels/{channelId}/messages` | Crew Member | — → `Page<CrewMessageDTO>` |
| `POST` | `/api/crews/{crewId}/channels/{channelId}/messages/{messageId}/convert-to-task` | Crew Member | `ConvertToTaskRequestDTO` → `TaskResponseDTO` |
| `PUT` | `/api/crews/{crewId}/transfer-ownership/{newOwnerId}` | Crew Owner | — → `200 OK` |
| `POST` | `/api/crews/{crewId}/projects/{projectId}` | Crew Member | — → `ProjectResponseDTO` |
| `DELETE` | `/api/crews/{crewId}/projects/{projectId}` | Crew Owner | — → `204 No Content` |

### 25. Whiteboards (`controller/WhiteboardController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/crews/{crewId}/whiteboards` | Crew Member | `WhiteboardRequestDTO` → `WhiteboardResponseDTO` (201) |
| `GET` | `/api/crews/{crewId}/whiteboards` | Crew Member | — → `List<WhiteboardResponseDTO>` |
| `PUT` | `/api/crews/{crewId}/whiteboards/{boardId}/snapshot` | Crew Member | `SnapshotRequestDTO` → `200 OK` |

### 26. Whiteboard WebSocket (`controller/WhiteboardSocketController.java`)
| Transport | Destination | Direction |
| :--- | :--- | :--- |
| `@MessageMapping` | `/whiteboards/{boardId}/draw` | Client → Server (stroke payload) |
| Broadcast | `/topic/whiteboards/{boardId}` | Server → Clients (live stroke data) |

### 27. Notifications (`controller/NotificationController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/notifications` | Authenticated | — → `Page<NotificationDTO>` (max 100/page) |
| `GET` | `/api/notifications/unread/count` | Authenticated | — → `{ "count": Long }` |
| `PUT` | `/api/notifications/{id}/read` | Authenticated | — → `204 No Content` |
| `PUT` | `/api/notifications/read-all` | Authenticated | — → `204 No Content` |
| `DELETE` | `/api/notifications/{id}` | Authenticated | — → `204 No Content` |

### 28. Notes (`controller/NoteController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/notes` | Authenticated | `NoteRequestDTO` → `NoteResponseDTO` |
| `GET` | `/api/notes` | Authenticated | — → `List<NoteResponseDTO>` |
| `PUT` | `/api/notes/{noteId}` | Authenticated | `NoteRequestDTO` → `NoteResponseDTO` |
| `DELETE` | `/api/notes/{noteId}` | Authenticated | — → `204 No Content` |

### 29. Focus Sessions (`controller/FocusSessionController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/focus/start` | Authenticated | `FocusSessionRequestDTO` → `FocusSessionDTO` |
| `POST` | `/api/focus/{id}/stop` | Authenticated | — → `FocusSessionDTO` |
| `GET` | `/api/focus` | Authenticated | — → `List<FocusSessionDTO>` |

### 30. Calendar Events (`controller/CalendarEventController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/calendar-events` | Authenticated | `CalendarEventRequestDTO` → `CalendarEventResponseDTO` |
| `GET` | `/api/calendar-events` | Authenticated | — → `List<CalendarEventResponseDTO>` |
| `PUT` | `/api/calendar-events/{id}` | Authenticated | `CalendarEventRequestDTO` → `CalendarEventResponseDTO` |
| `DELETE` | `/api/calendar-events/{id}` | Authenticated | — → `204 No Content` |

### 31. Saved Items / Bookmarks (`controller/SavedItemController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/saved-items` | Authenticated | `SavedItemRequestDTO` → `SavedItemResponseDTO` |
| `GET` | `/api/saved-items` | Authenticated | — → `List<SavedItemResponseDTO>` |
| `DELETE` | `/api/saved-items/{id}` | Authenticated | — → `204 No Content` |

### 32. Dashboard (`controller/DashboardController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/dashboard/stats` | Authenticated | — → `DashboardStatsDTO` (multi-scoped aggregates) |
| `GET` | `/api/dashboard/export/csv` | Authenticated | — → `text/csv` (CSV injection protected) |

### 33. Admin (`controller/AdminController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/users` | SuperAdmin | — → `List<UserResponseDTO>` |
| `PUT` | `/api/admin/users/{userId}/toggle-super-admin` | SuperAdmin | — → `UserResponseDTO` |

### 34. Global Roles (`controller/RoleController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/roles` | Authenticated | — → `List<RoleResponseDTO>` |
| `POST` | `/api/roles` | Authenticated | `RoleCreateRequestDTO` → `RoleResponseDTO` (201) |

### 35. User Roles (`controller/UserRoleController.java`)
| Method | Path | Permission | DTO In → DTO Out |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/users/{userId}/roles` | Authenticated | — → `List<RoleResponseDTO>` |
| `PUT` | `/api/users/{userId}/roles` | Authenticated | `List<Long>` (role IDs) → `200 OK` |
