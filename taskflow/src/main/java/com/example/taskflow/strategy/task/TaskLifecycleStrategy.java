package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskMode;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskRequestDTO;
import java.util.Set;

public interface TaskLifecycleStrategy {
    TaskMode getSupportedMode();

    boolean canCreate(User u, com.example.taskflow.dto.TaskRequestDTO request);
    boolean canView(User u, Task t);
    boolean canEdit(User u, Task t);
    boolean canDelete(User u, Task t);
    boolean canReassign(User u, Task t);
    boolean canArchive(User u, Task t);
    boolean canEditDependency(User u, Task t);
    boolean validateDependencyLink(Task source, Task target);
    Set<TaskStatus> allowedTransitions(Task t);
}
