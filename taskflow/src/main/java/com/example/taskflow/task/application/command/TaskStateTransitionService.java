package com.example.taskflow.task.application.command;

import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.response.TaskResponseDTO;

public interface TaskStateTransitionService {
    TaskResponseDTO completePersonalTask(Long taskId, User user);
    TaskResponseDTO submitTask(Long taskId, User user);
    TaskResponseDTO approveTask(Long taskId, User reviewer);
    TaskResponseDTO rejectTask(Long taskId, User reviewer, String reason);
    TaskResponseDTO recallTask(Long taskId, User user);
    TaskResponseDTO completeCrewTask(Long taskId, User user);
    TaskResponseDTO claimTask(Long taskId, User user);
}

