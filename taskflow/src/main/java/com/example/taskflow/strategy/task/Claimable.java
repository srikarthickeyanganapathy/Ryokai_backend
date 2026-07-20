package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

public interface Claimable {
    boolean canClaim(User u, Task t);
}
