package com.example.taskflow.dashboard.application;

import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.dashboard.dto.DashboardStatsDTO;
import com.example.taskflow.task.api.response.TaskStatusBreakdownDTO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
public interface DashboardStatsStrategy {

    List<TaskStatus> TERMINAL_STATUSES = Arrays.stream(TaskStatus.values())
            .filter(s -> s.isTerminal())
            .collect(Collectors.toList());

    boolean supports(String scope);

    DashboardStatsDTO computeStats(User user, Long orgId, Long crewId);

    default DashboardStatsDTO createDto(long total, long todo, long inReview, long done, long revisions, long overdue, long assignedToMe) {
        long denominator = (done + revisions + inReview + todo);
        long completionRate = denominator > 0 ? (done * 100) / denominator : 0;
        
        List<TaskStatusBreakdownDTO> statusBreakdown = new ArrayList<>();
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.IN_PROGRESS.name(), todo, "#FFC107"));
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.SUBMITTED.name(), inReview, "#17A2B8"));
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.APPROVED.name(), done, "#28A745"));
        statusBreakdown.add(new TaskStatusBreakdownDTO(TaskStatus.REJECTED.name(), revisions, "#DC3545"));
        
        return new DashboardStatsDTO(total, todo, inReview, done, revisions, overdue, assignedToMe, statusBreakdown, completionRate);
    }
}