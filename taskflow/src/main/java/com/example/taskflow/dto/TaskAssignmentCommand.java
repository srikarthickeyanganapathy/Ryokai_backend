package com.example.taskflow.dto;

import com.example.taskflow.domain.TaskScope;
import com.example.taskflow.domain.User;
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
