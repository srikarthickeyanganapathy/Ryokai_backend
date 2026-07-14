package com.example.taskflow.security;

public enum PermissionType {
    TASK_VIEW("View tasks"),
    TASK_CREATE("Create new tasks"),
    TASK_ASSIGN("Assign tasks to users"),
    TASK_EDIT("Edit task details"),
    TASK_DELETE("Delete tasks"),
    TASK_REVIEW("Review submitted tasks"),
    TASK_COMMENT("Add comments to tasks"),
    TASK_CHECKLIST_EDIT("Edit task checklists"),
    TASK_DEPENDENCY_EDIT("Edit task dependencies"),
    TASK_REASSIGN("Reassign tasks to different users"),
    TASK_ARCHIVE("Archive tasks"),
    USER_MANAGE("Manage user accounts"),
    ROLE_MANAGE("Manage roles and permissions");

    private final String description;

    PermissionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
