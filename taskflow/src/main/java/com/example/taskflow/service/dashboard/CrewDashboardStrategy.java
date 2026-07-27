package com.example.taskflow.service.dashboard;

import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.DashboardStatsDTO;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CrewDashboardStrategy implements DashboardStatsStrategy {

    private final TaskRepository taskRepository;
    private final CrewMemberRepository crewMemberRepository;

    @Override
    public boolean supports(String scope) {
        return "CREWS".equalsIgnoreCase(scope) || "CREW".equalsIgnoreCase(scope);
    }

    @Override
    public DashboardStatsDTO computeStats(User user, Long orgId, Long crewId) {
        if (crewId == null) {
            throw new IllegalArgumentException("Crew ID is required for crew dashboard view");
        }

        boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId());
        if (!isMember) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view stats for this crew.");
        }

        LocalDate now = LocalDate.now();

        long totalTasks = taskRepository.countForCrew(crewId);
        long todoCount = taskRepository.countForCrewByStatus(crewId, TaskStatus.IN_PROGRESS);
        long inReviewCount = taskRepository.countForCrewByStatus(crewId, TaskStatus.SUBMITTED);
        long doneCount = taskRepository.countForCrewByStatusIn(crewId, TERMINAL_STATUSES);
        long revisionsCount = taskRepository.countForCrewByStatus(crewId, TaskStatus.REJECTED);
        long overdueCount = taskRepository.countForCrewOverdue(crewId, now, TERMINAL_STATUSES);
        long assignedToMeCount = taskRepository.countForCrewAndAssignee(crewId, user.getId());

        return createDto(totalTasks, todoCount, inReviewCount, doneCount, revisionsCount, overdueCount, assignedToMeCount);
    }
}
