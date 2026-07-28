package com.example.taskflow.task.application.command;

import java.time.LocalDate;
import java.util.List;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.response.BulkAssignResponseDTO;
import com.example.taskflow.task.api.request.TaskAssignmentCommand;
import com.example.taskflow.task.api.response.TaskResponseDTO;

public interface TaskAssignmentService {
    TaskResponseDTO assignTask(TaskAssignmentCommand cmd);
}
