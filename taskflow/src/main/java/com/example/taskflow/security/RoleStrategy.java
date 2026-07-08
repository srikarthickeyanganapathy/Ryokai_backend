package com.example.taskflow.security;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

public interface RoleStrategy {

    boolean canAssign(User user);

    boolean canReview(User user, Task task);

    boolean canOverride(User user);

    boolean canViewTask(User user, Task task);

    boolean canEdit(User user, Task task);

    boolean canDelete(User user, Task task);

    boolean canReassign(User user, Task task);
}
