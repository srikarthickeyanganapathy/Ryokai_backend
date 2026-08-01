package com.example.taskflow.platform.application;

import com.example.taskflow.notification.domain.Notification;
import com.example.taskflow.notification.domain.PriorityTier;
import com.example.taskflow.notification.infrastructure.persistence.NotificationRepository;
import com.example.taskflow.task.application.execution.ExecutionContext;
import com.example.taskflow.task.application.execution.ExecutionEngineService;
import com.example.taskflow.task.application.query.TaskQueryService;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.task.api.response.*;
import com.example.taskflow.platform.api.dto.MissionControlDTO;
import com.example.taskflow.task.mapper.TaskResponseMapper;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.project.application.ProjectService;
import com.example.taskflow.team.application.TeamService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@Transactional(readOnly = true)
public class MissionControlService {

    private final ExecutionEngineService executionEngineService;
    private final TaskQueryService taskQueryService;
    private final NotificationRepository notificationRepository;
    private final TaskResponseMapper taskResponseMapper;
    private final ProjectService projectService;
    private final TeamService teamService;

    public MissionControlService(ExecutionEngineService executionEngineService,
                                 TaskQueryService taskQueryService,
                                 NotificationRepository notificationRepository,
                                 TaskResponseMapper taskResponseMapper,
                                 ProjectService projectService,
                                 TeamService teamService) {
        this.executionEngineService = executionEngineService;
        this.taskQueryService = taskQueryService;
        this.notificationRepository = notificationRepository;
        this.taskResponseMapper = taskResponseMapper;
        this.projectService = projectService;
        this.teamService = teamService;
    }

    public MissionControlDTO getPersonalContext(User user) {
        List<Task> tasks = taskQueryService.getRawTasksForUser(user, "PERSONAL", null, null);
        ExecutionContext context = executionEngineService.getExecutionContext(tasks, List.of(), null);
        
        return MissionControlDTO.builder()
                .workspaceMode("PERSONAL")
                .header(HeaderDTO.builder().eyebrow("Personal Space").title(getGreeting() + ", " + user.getUsername()).subtitle("Your private execution space. Focus on what matters.").build())
                .dailyBrief(buildDailyBrief(user, tasks))
                .focusPanel(buildFocusPanel(context.getFocusRecommendation()))
                .signalStrip(buildSignalStrip(user, "PERSONAL", null, null))
                .personalContext(PersonalContextDTO.builder()
                        .todaySummary("Ready to focus")
                        .waitingSummary("No blockers")
                        .comingNextSummary("Upcoming tasks")
                        .activeTasks(mapTasks(tasks.stream().limit(5).collect(Collectors.toList())))
                        .projects(projectService.getAllProjects(user, "PERSONAL", null, null))
                        .build())
                .executionQueue(ExecutionQueueDTO.builder().tasks(mapTasks(context.getQueueOrdering())).build())
                .widgetVisibility(WidgetVisibilityDTO.builder().showFocusPanel(true).showExecutionQueue(true).showContextRail(true).showDailyBrief(true).build())
                .resumeContext(context.getResumeContext())
                .build();
    }

    public MissionControlDTO getCrewContext(User user, Long crewId) {
        List<Task> tasks = taskQueryService.getRawTasksForUser(user, "CREWS", null, crewId);
        ExecutionContext context = executionEngineService.getExecutionContext(tasks, List.of(), null);
        
        return MissionControlDTO.builder()
                .workspaceMode("CREWS")
                .header(HeaderDTO.builder().eyebrow("Crew Space").title("Crew Dashboard").subtitle("Stay in sync with your crew.").build())
                .focusPanel(buildFocusPanel(context.getFocusRecommendation()))
                .signalStrip(buildSignalStrip(user, "CREWS", null, crewId))
                .crewContext(CrewContextDTO.builder()
                        .activeEmptyStateMessage("Crew is on track. No active tasks.")
                        .activeTasks(mapTasks(tasks.stream().limit(5).collect(Collectors.toList())))
                        .projects(projectService.getAllProjects(user, "CREWS", null, crewId))
                        .channels(new ArrayList<>()) // Can wire CrewChannelService
                        .recentActivity(new ArrayList<>())
                        .build())
                .executionQueue(ExecutionQueueDTO.builder().tasks(mapTasks(context.getQueueOrdering())).build())
                .widgetVisibility(WidgetVisibilityDTO.builder().showFocusPanel(true).showExecutionQueue(true).showContextRail(true).showDailyBrief(false).build())
                .resumeContext(context.getResumeContext())
                .build();
    }

    public MissionControlDTO getOrganizationContext(User user, Long orgId) {
        List<Task> tasks = taskQueryService.getRawTasksForUser(user, "ORG", null, null);
        ExecutionContext context = executionEngineService.getExecutionContext(tasks, List.of(), null);
        
        return MissionControlDTO.builder()
                .workspaceMode("ORG")
                .header(HeaderDTO.builder().eyebrow("Organization Space").title("Mission Control").subtitle("Organization-wide execution context.").build())
                .focusPanel(buildFocusPanel(context.getFocusRecommendation()))
                .signalStrip(buildSignalStrip(user, "ORG", orgId, null))
                .organizationContext(OrganizationContextDTO.builder()
                        .insights(OrgInsightsDTO.builder()
                                .narrativeInsights(List.of("All systems operational", "No major blockers detected"))
                                .membersCount(0)
                                .teamsCount(teamService.listOrgTeams(orgId, user).size())
                                .projectsCount(projectService.getAllProjects(user, "ORG", orgId, null).size())
                                .build())
                        .projects(projectService.getAllProjects(user, "ORG", orgId, null))
                        .teams(teamService.listOrgTeams(orgId, user))
                        .build())
                .executionQueue(ExecutionQueueDTO.builder().tasks(mapTasks(context.getQueueOrdering())).build())
                .widgetVisibility(WidgetVisibilityDTO.builder().showFocusPanel(true).showExecutionQueue(true).showContextRail(true).showDailyBrief(false).build())
                .resumeContext(context.getResumeContext())
                .build();
    }

    private String getGreeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private DailyBriefDTO buildDailyBrief(User user, List<Task> tasks) {
        return DailyBriefDTO.builder()
                .greeting(getGreeting() + ", " + user.getUsername())
                .focusTasksCount((int) tasks.stream().filter(t -> t.getCurrentStatus() == TaskStatus.IN_PROGRESS).count())
                .remindersCount(2)
                .meetingsCount(0)
                .completionStreak(6)
                .streakMessage("You're on a 6-day completion streak")
                .build();
    }

    private FocusPanelDTO buildFocusPanel(Task task) {
        if (task == null) return null;
        return FocusPanelDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .status(task.getCurrentStatus() != null ? task.getCurrentStatus().name() : "IN_PROGRESS")
                .progress(0)
                .remainingTime("2 hours")
                .dueDate(task.getDueDate())
                .nextChecklistItem("Complete initial review")
                .blockers(new ArrayList<>())
                .collaborators(new ArrayList<>())
                .priority(task.getPriority() != null ? task.getPriority().name() : "MEDIUM")
                .estimatedCompletion("Today")
                .build();
    }

    private SignalStripDTO buildSignalStrip(User user, String workspaceMode, Long orgId, Long crewId) {
        List<Notification> rawSignals = notificationRepository.findActiveSignalsByWorkspace(user.getId(), PriorityTier.ARCHIVE, workspaceMode, orgId, crewId, PageRequest.of(0, 50)).getContent();
        
        // Backend Aggregation Rule: deterministically group signals
        List<ActionSummaryDTO> summaries = new ArrayList<>();
        if (!rawSignals.isEmpty()) {
            summaries.add(ActionSummaryDTO.builder()
                    .id("actions_req")
                    .type("AGGREGATED")
                    .title(rawSignals.size() + " Actions Required")
                    .message("Review pending signals")
                    .count(rawSignals.size())
                    .build());
        }
        return SignalStripDTO.builder()
                .actions(summaries)
                .totalRequiredActions(rawSignals.size())
                .build();
    }

    private TaskResponseDTO mapTask(Task task) {
        if (task == null) return null;
        return taskResponseMapper.mapToTaskResponseDTO(task);
    }

    private List<TaskResponseDTO> mapTasks(List<Task> tasks) {
        if (tasks == null) return List.of();
        return tasks.stream().map(this::mapTask).collect(Collectors.toList());
    }
}
