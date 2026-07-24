package com.example.taskflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.dto.ActivityLogDTO;
import com.example.taskflow.repository.ProjectActivityLogRepository;
import com.example.taskflow.repository.TaskActivityLogRepository;

@Service
@Transactional(readOnly = true)
public class ActivityLogService {

    private final TaskActivityLogRepository taskActivityLogRepository;
    private final ProjectActivityLogRepository projectActivityLogRepository;

    public ActivityLogService(
            TaskActivityLogRepository taskActivityLogRepository,
            ProjectActivityLogRepository projectActivityLogRepository) {
        this.taskActivityLogRepository = taskActivityLogRepository;
        this.projectActivityLogRepository = projectActivityLogRepository;
    }

    public Page<ActivityLogDTO> getTaskActivityLogs(Long taskId, Pageable pageable) {
        return taskActivityLogRepository
                .findByTaskIdOrderByCreatedAtDesc(taskId, pageable)
                .map(ActivityLogDTO::fromTaskLog);
    }

    public Page<ActivityLogDTO> getProjectActivityLogs(Long projectId, Pageable pageable) {
        return projectActivityLogRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(ActivityLogDTO::fromProjectLog);
    }
}
