package com.example.taskflow.security;
import com.example.taskflow.organization.rbac.domain.Permission;

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