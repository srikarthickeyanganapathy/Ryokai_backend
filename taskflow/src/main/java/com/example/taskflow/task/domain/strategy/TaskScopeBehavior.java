package com.example.taskflow.task.domain.strategy;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;

public interface TaskScopeBehavior {
    TaskStatus initialStatus();
    boolean canBeReviewed();
    boolean canBeSubmitted();
    void onComplete(Task t, User u);
}
