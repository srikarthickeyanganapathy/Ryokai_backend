package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

public interface Approvable {
    boolean canSubmit(User u, Task t);
    boolean canApprove(User u, Task t);
    boolean canReject(User u, Task t);
}
