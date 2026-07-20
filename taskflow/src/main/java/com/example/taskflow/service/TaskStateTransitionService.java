package com.example.taskflow.service;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskResponseDTO;

public interface TaskStateTransitionService {
    TaskResponseDTO completePersonalTask(Long taskId, User user);
    TaskResponseDTO submitTask(Long taskId, User user);
    TaskResponseDTO approveTask(Long taskId, User reviewer);
    TaskResponseDTO rejectTask(Long taskId, User reviewer, String reason);
    TaskResponseDTO recallTask(Long taskId, User user);
    TaskResponseDTO completeCrewTask(Long taskId, User user);
    TaskResponseDTO claimTask(Long taskId, User user);
}
