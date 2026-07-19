package com.example.taskflow.security;

public enum PermissionType {
    TASK_VIEW("View tasks"),
    TASK_ASSIGN("Assign tasks to users"),
    TASK_EDIT("Edit task details"),
    TASK_DELETE("Delete tasks"),
    TASK_REVIEW("Review submitted tasks"),
    TASK_DEPENDENCY_EDIT("Edit task dependencies"),
    TASK_REASSIGN("Reassign tasks to different users"),
    TASK_ARCHIVE("Archive tasks"),
    ROLE_MANAGE("Manage roles and permissions"),
    ORG_MEMBER_INVITE("Invite users to the organization"),
    ORG_MEMBER_REMOVE("Remove users from the organization"),
    LEAVE_REQUEST_MANAGE("Manage leave requests"),
    TEAM_CREATE("Create new teams"),
    TEAM_MANAGE("Manage team settings and rosters"),
    PROJECT_CREATE("Create new projects"),
    PROJECT_MANAGE("Manage global project metadata"),
    TASK_OVERRIDE("Override task dependencies and review constraints"),
    ANNOUNCEMENT_MANAGE("Manage organization announcements");

    private final String description;

    PermissionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
