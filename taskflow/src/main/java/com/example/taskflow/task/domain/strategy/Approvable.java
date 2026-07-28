package com.example.taskflow.task.domain.strategy;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;

public interface Approvable {
    boolean canSubmit(User u, Task t);
    boolean canApprove(User u, Task t);
    boolean canReject(User u, Task t);
}
