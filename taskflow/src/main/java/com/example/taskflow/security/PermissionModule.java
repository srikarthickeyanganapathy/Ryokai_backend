package com.example.taskflow.security;
import com.example.taskflow.goal.domain.Goal;
import com.example.taskflow.organization.announcement.domain.Announcement;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.team.domain.Team;

/**
 * Permission modules â€” groups permissions by the resource they protect.
 * Used for UI categorization and filtering.
 */
public enum PermissionModule {
    ORGANIZATION("Organization"),
    MEMBER("Members"),
    TEAM("Teams"),
    PROJECT("Projects"),
    TASK("Tasks"),
    GOAL("Goals"),
    ANNOUNCEMENT("Announcements"),
    LEAVE("Leave Management"),
    CALENDAR("Calendar"),
    DASHBOARD("Dashboard & Analytics"),
    ACTIVITY("Activity History"),
    ROLE("Roles & Permissions");

    private final String displayName;

    PermissionModule(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}