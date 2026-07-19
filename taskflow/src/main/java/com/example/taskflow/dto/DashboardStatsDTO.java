package com.example.taskflow.dto;

import java.util.List;

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
