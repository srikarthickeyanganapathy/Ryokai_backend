package com.example.taskflow.task.application.command;
import com.example.taskflow.task.api.request.TaskAssignmentCommand;
import com.example.taskflow.task.api.response.TaskResponseDTO;

public interface TaskAssignmentService {
    TaskResponseDTO assignTask(TaskAssignmentCommand cmd);
}
