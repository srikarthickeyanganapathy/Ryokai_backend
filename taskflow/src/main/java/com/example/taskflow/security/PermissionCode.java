package com.example.taskflow.security;

/**
 * Complete permission taxonomy for Aura's Organization RBAC system.
 * 83 explicit permissions across 12 modules.
 *
 * <p>Naming convention: {@code RESOURCE_ACTION} in SCREAMING_SNAKE_CASE.
 * Permissions describe <b>what</b> can be done, never <b>where</b> (scope handles that).
 *
 * <p>This enum replaces the legacy {@link PermissionType} enum.
 * Personal and Crew workspaces do NOT use this permission system.
 */
public enum PermissionCode {

    // =====================================================================
    // ORGANIZATION (7)
    // =====================================================================
    ORG_VIEW("View organization details", PermissionModule.ORGANIZATION, PermissionCategory.CRUD, true),
    ORG_PROFILE_UPDATE("Update organization profile (name, description, logo)", PermissionModule.ORGANIZATION, PermissionCategory.SETTINGS, false),
    ORG_ARCHIVE("Archive the organization", PermissionModule.ORGANIZATION, PermissionCategory.LIFECYCLE, false),
    ORG_RESTORE("Restore an archived organization", PermissionModule.ORGANIZATION, PermissionCategory.LIFECYCLE, false),
    ORG_SETTINGS_VIEW("View organization settings and configuration", PermissionModule.ORGANIZATION, PermissionCategory.SETTINGS, true),
    ORG_SETTINGS_UPDATE("Modify organization settings and configuration", PermissionModule.ORGANIZATION, PermissionCategory.SETTINGS, false),
    ORG_TRANSFER_OWNERSHIP("Transfer organization ownership to another member", PermissionModule.ORGANIZATION, PermissionCategory.WORKFLOW, false),

    // =====================================================================
    // MEMBERS (8)
    // =====================================================================
    MEMBER_VIEW("View member profiles and directory", PermissionModule.MEMBER, PermissionCategory.CRUD, true),
    MEMBER_INVITE("Send membership invitations", PermissionModule.MEMBER, PermissionCategory.MEMBERSHIP, false),
    MEMBER_REMOVE("Remove a member from the organization", PermissionModule.MEMBER, PermissionCategory.MEMBERSHIP, false),
    MEMBER_ROLE_UPDATE("Change a member's assigned role", PermissionModule.MEMBER, PermissionCategory.MEMBERSHIP, false),
    MEMBER_SUSPEND("Temporarily suspend a member's access", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),
    MEMBER_REACTIVATE("Reactivate a suspended member", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),
    MEMBER_EXPORT("Export member directory data", PermissionModule.MEMBER, PermissionCategory.EXPORT, false),
    MEMBER_EXIT_APPROVE("Approve or reject organization exit requests", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),
    EXIT_REQUEST_CREATE("Submit an organization exit request", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),
    EXIT_REQUEST_VIEW("View organization exit requests", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),
    EXIT_REQUEST_APPROVE("Approve organization exit requests", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),
    EXIT_REQUEST_REJECT("Reject organization exit requests", PermissionModule.MEMBER, PermissionCategory.WORKFLOW, false),

    // =====================================================================
    // TEAMS (8)
    // =====================================================================
    TEAM_VIEW("View team details and roster", PermissionModule.TEAM, PermissionCategory.CRUD, false),
    TEAM_CREATE("Create new teams", PermissionModule.TEAM, PermissionCategory.CRUD, false),
    TEAM_UPDATE("Update team metadata", PermissionModule.TEAM, PermissionCategory.CRUD, false),
    TEAM_DELETE("Delete a team", PermissionModule.TEAM, PermissionCategory.CRUD, false),
    TEAM_ARCHIVE("Archive a team", PermissionModule.TEAM, PermissionCategory.LIFECYCLE, false),
    TEAM_MEMBER_ADD("Add members to a team", PermissionModule.TEAM, PermissionCategory.MEMBERSHIP, false),
    TEAM_MEMBER_REMOVE("Remove members from a team", PermissionModule.TEAM, PermissionCategory.MEMBERSHIP, false),
    TEAM_MEMBER_ROLE_UPDATE("Change a member's role within the team", PermissionModule.TEAM, PermissionCategory.MEMBERSHIP, false),

    // =====================================================================
    // PROJECTS (11)
    // =====================================================================
    PROJECT_VIEW("View project details and metadata", PermissionModule.PROJECT, PermissionCategory.CRUD, false),
    PROJECT_CREATE("Create new projects", PermissionModule.PROJECT, PermissionCategory.CRUD, false),
    PROJECT_UPDATE("Update project metadata", PermissionModule.PROJECT, PermissionCategory.CRUD, false),
    PROJECT_DELETE("Permanently delete a project", PermissionModule.PROJECT, PermissionCategory.CRUD, false),
    PROJECT_ARCHIVE("Archive a project", PermissionModule.PROJECT, PermissionCategory.LIFECYCLE, false),
    PROJECT_RESTORE("Restore an archived project", PermissionModule.PROJECT, PermissionCategory.LIFECYCLE, false),
    PROJECT_SETTINGS_UPDATE("Modify project-level settings", PermissionModule.PROJECT, PermissionCategory.SETTINGS, false),
    PROJECT_MEMBER_ADD("Add collaborators to a project", PermissionModule.PROJECT, PermissionCategory.MEMBERSHIP, false),
    PROJECT_MEMBER_REMOVE("Remove collaborators from a project", PermissionModule.PROJECT, PermissionCategory.MEMBERSHIP, false),
    PROJECT_MEMBER_ROLE_UPDATE("Change a collaborator's project role", PermissionModule.PROJECT, PermissionCategory.MEMBERSHIP, false),
    PROJECT_EXPORT("Export project data", PermissionModule.PROJECT, PermissionCategory.EXPORT, false),

    // =====================================================================
    // TASKS (17)
    // =====================================================================
    TASK_VIEW("View task details", PermissionModule.TASK, PermissionCategory.CRUD, true),
    TASK_CREATE("Create new tasks", PermissionModule.TASK, PermissionCategory.CRUD, false),
    TASK_UPDATE("Update task fields", PermissionModule.TASK, PermissionCategory.CRUD, false),
    TASK_COMPLETE("Complete a task", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_RECALL("Recall a task", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_DELETE("Permanently delete a task", PermissionModule.TASK, PermissionCategory.CRUD, false),
    TASK_ARCHIVE("Archive a task", PermissionModule.TASK, PermissionCategory.LIFECYCLE, false),
    TASK_RESTORE("Restore an archived task", PermissionModule.TASK, PermissionCategory.LIFECYCLE, false),
    TASK_ASSIGN("Assign a task to a user", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_REASSIGN("Reassign a task to a different user", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_START("Transition a task to in-progress", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_SUBMIT("Submit a task for review", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_APPROVE("Approve a submitted task", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_REJECT("Reject a submitted task", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_REOPEN("Reopen a completed or closed task", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_CANCEL("Cancel a task without completion", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_OVERRIDE("Override workflow constraints", PermissionModule.TASK, PermissionCategory.WORKFLOW, false),
    TASK_DEPENDENCY_UPDATE("Add, remove, or modify task dependencies", PermissionModule.TASK, PermissionCategory.SETTINGS, false),
    TASK_COMMENT_CREATE("Create comments on tasks", PermissionModule.TASK, PermissionCategory.CRUD, false),

    // =====================================================================
    // GOALS (7)
    // =====================================================================
    GOAL_VIEW("View goals and key results", PermissionModule.GOAL, PermissionCategory.CRUD, true),
    GOAL_CREATE("Create new goals with key results", PermissionModule.GOAL, PermissionCategory.CRUD, false),
    GOAL_UPDATE("Update goal metadata and progress", PermissionModule.GOAL, PermissionCategory.CRUD, false),
    GOAL_DELETE("Delete a goal", PermissionModule.GOAL, PermissionCategory.CRUD, false),
    GOAL_ARCHIVE("Archive a completed or abandoned goal", PermissionModule.GOAL, PermissionCategory.LIFECYCLE, false),
    GOAL_ASSIGN("Assign ownership of a goal", PermissionModule.GOAL, PermissionCategory.WORKFLOW, false),
    GOAL_PROGRESS_UPDATE("Update key result progress values", PermissionModule.GOAL, PermissionCategory.WORKFLOW, false),

    // =====================================================================
    // ANNOUNCEMENTS (5)
    // =====================================================================
    ANNOUNCEMENT_VIEW("View announcements", PermissionModule.ANNOUNCEMENT, PermissionCategory.CRUD, true),
    ANNOUNCEMENT_CREATE("Create new announcements", PermissionModule.ANNOUNCEMENT, PermissionCategory.CRUD, false),
    ANNOUNCEMENT_UPDATE("Edit existing announcements", PermissionModule.ANNOUNCEMENT, PermissionCategory.CRUD, false),
    ANNOUNCEMENT_DELETE("Delete announcements", PermissionModule.ANNOUNCEMENT, PermissionCategory.CRUD, false),
    ANNOUNCEMENT_PIN("Pin or unpin an announcement", PermissionModule.ANNOUNCEMENT, PermissionCategory.WORKFLOW, false),

    // =====================================================================
    // LEAVE MANAGEMENT (7)
    // =====================================================================
    LEAVE_VIEW("View leave requests", PermissionModule.LEAVE, PermissionCategory.CRUD, true),
    LEAVE_CREATE("Submit a leave request", PermissionModule.LEAVE, PermissionCategory.CRUD, false),
    LEAVE_UPDATE("Modify a pending leave request", PermissionModule.LEAVE, PermissionCategory.CRUD, false),
    LEAVE_DELETE("Cancel or delete a leave request", PermissionModule.LEAVE, PermissionCategory.CRUD, false),
    LEAVE_APPROVE("Approve a pending leave request", PermissionModule.LEAVE, PermissionCategory.WORKFLOW, false),
    LEAVE_REJECT("Reject a pending leave request", PermissionModule.LEAVE, PermissionCategory.WORKFLOW, false),
    LEAVE_SETTINGS_UPDATE("Configure leave policies", PermissionModule.LEAVE, PermissionCategory.SETTINGS, false),

    // =====================================================================
    // CALENDAR (5)
    // =====================================================================
    CALENDAR_VIEW("View calendar events", PermissionModule.CALENDAR, PermissionCategory.CRUD, true),
    CALENDAR_CREATE("Create calendar events", PermissionModule.CALENDAR, PermissionCategory.CRUD, false),
    CALENDAR_UPDATE("Modify calendar events", PermissionModule.CALENDAR, PermissionCategory.CRUD, false),
    CALENDAR_DELETE("Delete calendar events", PermissionModule.CALENDAR, PermissionCategory.CRUD, false),
    CALENDAR_EXPORT("Export calendar data", PermissionModule.CALENDAR, PermissionCategory.EXPORT, false),

    // =====================================================================
    // DASHBOARD & ANALYTICS (3)
    // =====================================================================
    DASHBOARD_VIEW("View dashboard metrics (visibility determined by scope)", PermissionModule.DASHBOARD, PermissionCategory.CRUD, true),
    DASHBOARD_EXPORT("Export dashboard reports and analytics", PermissionModule.DASHBOARD, PermissionCategory.EXPORT, false),
    DASHBOARD_WIDGET_UPDATE("Customize dashboard layout and widgets", PermissionModule.DASHBOARD, PermissionCategory.SETTINGS, false),

    // =====================================================================
    // ACTIVITY HISTORY (2)
    // =====================================================================
    ACTIVITY_VIEW("View activity logs (visibility determined by scope)", PermissionModule.ACTIVITY, PermissionCategory.CRUD, true),
    ACTIVITY_EXPORT("Export activity history", PermissionModule.ACTIVITY, PermissionCategory.EXPORT, false),

    // =====================================================================
    // ROLES & PERMISSIONS (4)
    // =====================================================================
    ROLE_VIEW("View roles and their assigned permissions", PermissionModule.ROLE, PermissionCategory.CRUD, true),
    ROLE_CREATE("Create new custom roles", PermissionModule.ROLE, PermissionCategory.CRUD, false),
    ROLE_UPDATE("Modify role name, description, and permissions", PermissionModule.ROLE, PermissionCategory.CRUD, false),
    ROLE_DELETE("Delete custom roles", PermissionModule.ROLE, PermissionCategory.CRUD, false);

    private final String description;
    private final PermissionModule module;
    private final PermissionCategory category;
    private final boolean membershipIntrinsic;

    PermissionCode(String description, PermissionModule module, PermissionCategory category, boolean membershipIntrinsic) {
        this.description = description;
        this.module = module;
        this.category = category;
        this.membershipIntrinsic = membershipIntrinsic;
    }

    public String getDescription() {
        return description;
    }

    public PermissionModule getModule() {
        return module;
    }

    public PermissionCategory getCategory() {
        return category;
    }

    public boolean isMembershipIntrinsic() {
        return membershipIntrinsic;
    }

    /**
     * Returns the permission code as stored in the database (enum name).
     */
    public String code() {
        return name();
    }
}