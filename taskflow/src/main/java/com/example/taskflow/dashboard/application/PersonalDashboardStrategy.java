package com.example.taskflow.dashboard.application;

import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.dashboard.dto.DashboardStatsDTO;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import com.example.taskflow.organization.rbac.domain.Scope;

@Component
@RequiredArgsConstructor
public class PersonalDashboardStrategy implements DashboardStatsStrategy {

    private final TaskRepository taskRepository;

    @Override
    public boolean supports(String scope) {
        return "PERSONAL".equalsIgnoreCase(scope);
    }

    @Override
    public DashboardStatsDTO computeStats(User user, Long orgId, Long crewId) {
        LocalDate now = LocalDate.now();
        Long uid = user.getId();

        long totalTasks = taskRepository.countPersonalTasks(uid);
        long todoCount = taskRepository.countPersonalTasksByStatus(uid, TaskStatus.IN_PROGRESS);
        long inReviewCount = taskRepository.countPersonalTasksByStatus(uid, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countPersonalTasksByStatusIn(uid, TERMINAL_STATUSES);
        long revisionsCount = taskRepository.countPersonalTasksByStatus(uid, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countPersonalTasksOverdue(uid, now, TERMINAL_STATUSES);

        long assignedToMeCount = totalTasks;

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }
}