package com.example.taskflow.task.api.request;

import com.example.taskflow.task.domain.model.TaskScope;
import com.example.taskflow.user.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TaskAssignmentCommand {
    private final TaskRequestDTO request;
    private final User assignor;
    private final User assignee;
    private final TaskScope scope;
}