package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;

public interface TaskScopeBehavior {
    TaskStatus initialStatus();
    boolean canBeReviewed();
    boolean canBeSubmitted();
    void onComplete(Task t, User u);
}
