package com.example.taskflow.security;
/**
 * Permission action categories.
 * Each permission belongs to exactly one category, independent of its module.
 */
public enum PermissionCategory {
    CRUD,
    WORKFLOW,
    LIFECYCLE,
    MEMBERSHIP,
    SETTINGS,
    EXPORT
}