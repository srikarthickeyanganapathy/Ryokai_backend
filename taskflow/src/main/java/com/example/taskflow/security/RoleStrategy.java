package com.example.taskflow.security;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

public interface RoleStrategy {

    boolean canAssign(User user);

    boolean canReview(User user, Task task);

    boolean canOverride(User user);

    boolean canViewTask(User user, Task task);
    
    boolean canViewAllTasks(User user);

    boolean canEdit(User user, Task task);

    boolean canDelete(User user, Task task);

    boolean canReassign(User user, Task task);

    /**
     * RB-M04 fix: dedicated archive permission. Previously ARCHIVE was routed
     * to canDelete in CustomPermissionEvaluator, but TaskController.toggleArchive
     * used 'EDIT' — so the ARCHIVE branch was dead code and an assignee (who can
     * edit but not delete) could archive a task. canArchive is intentionally
     * stricter than canEdit but looser than canDelete (creator + assignee +
     * org manager+).
     */
    boolean canArchive(User user, Task task);

    /**
     * Spec: dependencies are "assignor-locked" — only the task creator
     * (assignor) and org admin/director can add/remove dependencies.
     * The assignee is explicitly blocked from editing dependencies.
     */
    boolean canEditDependency(User user, Task task);
}
