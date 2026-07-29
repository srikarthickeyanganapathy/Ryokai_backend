package com.example.taskflow.security;

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