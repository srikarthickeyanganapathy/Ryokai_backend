package com.example.taskflow.dashboard.dto;

import java.util.List;
import com.example.taskflow.task.api.response.TaskStatusBreakdownDTO;

public record DashboardStatsDTO(
        long totalTasks,
        long todoCount,           // IN_PROGRESS
        long inReviewCount,       // SUBMITTED
        long doneCount,           // APPROVED
        long revisionsCount,      // REJECTED
        long overdueCount,
        long assignedToMeCount,
        List<TaskStatusBreakdownDTO> statusBreakdown,
        long myCompletionRate     // percentage
) {}