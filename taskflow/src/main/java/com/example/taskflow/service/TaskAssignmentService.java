package com.example.taskflow.service;

import java.time.LocalDate;
import java.util.List;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.BulkAssignResponseDTO;
import com.example.taskflow.dto.TaskAssignmentCommand;
import com.example.taskflow.dto.TaskResponseDTO;

public interface TaskAssignmentService {
    TaskResponseDTO assignTask(TaskAssignmentCommand cmd);
}
