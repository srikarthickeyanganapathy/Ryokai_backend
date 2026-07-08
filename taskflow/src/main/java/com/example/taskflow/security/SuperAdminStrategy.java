package com.example.taskflow.security;

import org.springframework.stereotype.Component;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

/**
 * Super Admin Strategy — Rule 8 compliant.
 * Super Admin can VIEW everything but does NOT participate in org-level
 * task workflows (assign, review, edit, delete, reassign).
 * These powers are reserved for org-scoped roles (ADMIN, DIRECTOR, MANAGER).
 */
@Component
public class SuperAdminStrategy implements RoleStrategy {

    @Override
    public boolean canAssign(User user) {
        // Rule 8: Super Admin does NOT assign tasks within orgs
        return false;
    }

    @Override
    public boolean canReview(User user, Task task) {
        if (task != null && task.isPersonal() && task.getOrganization() == null) {
            return (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId())) ||
                   (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId()));
        }
        // Rule 8: Super Admin does NOT review org tasks
        return false;
    }

    @Override
    public boolean canOverride(User user) {
        // Still true — used for READ-ONLY visibility (task listing, dashboard stats)
        return true;
    }

    @Override
    public boolean canViewTask(User user, Task task) {
        // Super Admin can view any task (platform oversight)
        return true;
    }
    
    @Override
    public boolean canEdit(User user, Task task) {
        if (task != null && task.isPersonal() && task.getOrganization() == null) {
            return (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId())) ||
                   (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId()));
        }
        // Rule 8: Super Admin does NOT edit org tasks
        return false;
    }

    @Override
    public boolean canDelete(User user, Task task) {
        if (task != null && task.isPersonal() && task.getOrganization() == null) {
            return (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId()));
        }
        // Rule 8: Super Admin does NOT delete org tasks
        return false;
    }

    @Override
    public boolean canReassign(User user, Task task) {
        if (task != null && task.isPersonal() && task.getOrganization() == null) {
            return (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId()));
        }
        // Rule 8: Super Admin does NOT reassign org tasks
        return false;
    }
}
