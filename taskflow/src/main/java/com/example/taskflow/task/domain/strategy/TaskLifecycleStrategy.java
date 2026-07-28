package com.example.taskflow.task.domain.strategy;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskMode;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.request.TaskRequestDTO;
import java.util.Set;

public interface TaskLifecycleStrategy {
    TaskMode getSupportedMode();

    boolean canCreate(User u, com.example.taskflow.task.api.request.TaskRequestDTO request);
    boolean canView(User u, Task t);
    boolean canEdit(User u, Task t);
    boolean canDelete(User u, Task t);
    boolean canReassign(User u, Task t);
    boolean canArchive(User u, Task t);
    boolean canEditDependency(User u, Task t);
    boolean validateDependencyLink(Task source, Task target);
    Set<TaskStatus> allowedTransitions(Task t);
}
