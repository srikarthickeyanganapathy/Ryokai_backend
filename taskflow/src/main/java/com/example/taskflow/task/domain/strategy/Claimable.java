package com.example.taskflow.task.domain.strategy;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;

public interface Claimable {
    boolean canClaim(User u, Task t);
}
