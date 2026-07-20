package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskPriority;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.Team;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Project;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.repository.CrewRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TeamRepository;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

@Service
public class TaskAssignmentServiceImpl implements TaskAssignmentService {

    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;
    private final NotificationService notificationService;
    private final TeamRepository teamRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final CrewRepository crewRepository;
    private final TaskHierarchyValidator taskHierarchyValidator;

    @Value("${app.reminders.timezone:Asia/Kolkata}")
    private String timezoneProperty;

    private ZoneId zoneId;

    public TaskAssignmentServiceImpl(TaskRepository taskRepository,
                                     TaskAuditService taskAuditService,
                                     NotificationService notificationService,
                                     TeamRepository teamRepository,
                                     OrganizationMembershipRepository membershipRepository,
                                     CrewRepository crewRepository,
                                     TaskHierarchyValidator taskHierarchyValidator) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.notificationService = notificationService;
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.crewRepository = crewRepository;
        this.taskHierarchyValidator = taskHierarchyValidator;
    }

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezoneProperty);
    }

    @Override
    @Transactional
    public TaskResponseDTO assignTask(com.example.taskflow.dto.TaskAssignmentCommand cmd) {
        String title = cmd.getRequest().getTitle();
        String description = cmd.getRequest().getDescription();
        User assignee = cmd.getAssignee();
        User creator = cmd.getAssignor();
        TaskPriority priority = cmd.getRequest().getPriority();
        java.time.LocalDate dueDate = cmd.getRequest().getDueDate();
        String tags = cmd.getRequest().getTags();
        boolean isPersonal = cmd.getScope().getMode() == com.example.taskflow.domain.TaskMode.PERSONAL;
        Long teamId = cmd.getScope().getTeamId();
        Long projectId = cmd.getRequest().getProjectId();
        Long crewId = cmd.getScope().getCrewId();

        // Validate Assignments using TaskHierarchyValidator
        if (crewId != null) {
            taskHierarchyValidator.validateCrewTask(crewId, creator, assignee);
        } else if (!isPersonal) {
            boolean isSuperAdmin = creator.isSuperAdmin();
            taskHierarchyValidator.validateOrgOrTeamTask(creator, assignee, teamId, isSuperAdmin);
        } else {
            taskHierarchyValidator.validatePersonalTask(creator, assignee);
        }

        if (dueDate != null && dueDate.isBefore(java.time.LocalDate.now(zoneId))) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setAssignee(assignee); // null for unclaimed crew tasks
        task.setCreator(creator);

        // Team/Org scoping
        if (teamId != null) {
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new IllegalArgumentException("Team not found"));
            task.setTeam(team);
            task.setOrg(team.getOrganization());
            task.setCrew(null);
        } else if (!isPersonal && crewId == null) {
            var memberships = membershipRepository.findByUserId(creator.getId());
            if (!memberships.isEmpty()) {
                task.setOrg(memberships.get(0).getOrganization());
            } else {
                if (assignee != null) {
                    var assigneeMemberships = membershipRepository.findByUserId(assignee.getId());
                    if (!assigneeMemberships.isEmpty()) {
                        task.setOrg(assigneeMemberships.get(0).getOrganization());
                    }
                }
            }
            task.setCrew(null);
        } else if (isPersonal) {
            task.setOrg(null);
            task.setTeam(null);
            task.setCrew(null);
        }
        
        // Crew scoping
        if (crewId != null) {
            com.example.taskflow.domain.Crew crew = crewRepository.findById(crewId)
                    .orElseThrow(() -> new IllegalArgumentException("Crew not found"));
            task.setCrew(crew);
            task.setOrg(null);
            task.setTeam(null);
            task.setPersonal(false);
        }

        task.setPriority(priority != null ? priority : TaskPriority.MEDIUM);
        task.setDueDate(dueDate);
        task.setTags(tags);
        task.setPersonal(isPersonal);

        if (isPersonal) {
            task.setCurrentStatus(TaskStatus.TODO);
        } else if (crewId != null) {
            task.setCurrentStatus(assignee != null ? TaskStatus.IN_PROGRESS : TaskStatus.TODO);
        } else {
            task.setCurrentStatus(TaskStatus.IN_PROGRESS);
        }

        if (projectId != null) {
            taskHierarchyValidator.validateProjectForTask(projectId, task, isPersonal, assignee);
        }

        Task savedTask = taskRepository.save(task);
        taskAuditService.recordStatus(savedTask, null, savedTask.getCurrentStatus().name(), savedTask.getCurrentStatus().name(), creator, null);

        if (assignee != null) {
            notificationService.createAndSend(
                assignee,
                creator,
                com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
                "You have a new task: " + title,
                description,
                savedTask,
                null,
                creator
            );
        }

        return mapToTaskResponseDTO(savedTask);
    }

    private TaskResponseDTO mapToTaskResponseDTO(Task task) {
        return new TaskResponseDTO(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getAssignee() != null ? task.getAssignee().getUsername() : null,
            task.getCreator() != null ? task.getCreator().getUsername() : null,
            task.getReviewer() != null ? task.getReviewer().getUsername() : null,
            task.getCurrentStatus(),
            task.getPriority(),
            task.getDueDate(),
            task.getTags(),
            task.getChecklists() != null ? task.getChecklists().stream().map(item -> new com.example.taskflow.dto.ChecklistItemDTO(item.getId(), item.getText(), item.getIsCompleted(), item.getDisplayOrder(), item.getCreatedBy() != null ? item.getCreatedBy().getUsername() : null)).collect(Collectors.toList()) : java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            task.getCreatedAt(),
            task.getUpdatedAt(),
            task.getCoverImageUrl(),
            task.getRejectionReason(),
            task.isPersonal(),
            task.isArchived(),
            task.getOrg() != null ? task.getOrg().getId() : null,
            task.getOrg() != null ? task.getOrg().getName() : null,
            task.getTeam() != null ? task.getTeam().getId() : null,
            task.getTeam() != null ? task.getTeam().getName() : null,
            task.getProject() != null ? task.getProject().getId() : null,
            task.getProject() != null ? task.getProject().getName() : null,
            task.getCrew() != null ? task.getCrew().getId() : null,
            task.getCrew() != null ? task.getCrew().getName() : null
        );
    }
}
