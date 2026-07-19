package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.TaskStatus;

import com.example.taskflow.domain.ChecklistItem;
import com.example.taskflow.repository.TaskStatusHistoryRepository;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskComment;
import com.example.taskflow.domain.TaskDependency;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.dto.ChecklistItemDTO;
import com.example.taskflow.dto.TaskCommentDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.dto.TaskSummaryDTO;
import com.example.taskflow.dto.TaskUpdateRequestDTO;
import com.example.taskflow.repository.ChecklistItemRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.TaskCommentRepository;
import com.example.taskflow.repository.TaskDependencyRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TeamRepository;
import com.example.taskflow.repository.ProjectRepository;
import com.example.taskflow.repository.TeamMemberRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.domain.Project;
import com.example.taskflow.security.RoleStrategy;
import com.example.taskflow.security.RoleStrategyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.HashSet;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;

@Service
public class TaskWorkflowService {

    private final TaskRepository taskRepository;
    private final TaskAuditService taskAuditService;
    private final RoleStrategyFactory roleStrategyFactory;
    private final TaskCommentRepository taskCommentRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final NotificationService notificationService;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final TaskStatusHistoryRepository taskStatusHistoryRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final com.example.taskflow.repository.TaskEvidenceRepository taskEvidenceRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CrewMemberRepository crewMemberRepository;

    @Value("${app.reminders.timezone:Asia/Kolkata}")
    private String timezoneProperty;

    private ZoneId zoneId;

    public TaskWorkflowService(TaskRepository taskRepository,
                               TaskAuditService taskAuditService,
                               RoleStrategyFactory roleStrategyFactory,
                               TaskCommentRepository taskCommentRepository,
                               ChecklistItemRepository checklistItemRepository,
                               TaskDependencyRepository taskDependencyRepository,
                               NotificationService notificationService,
                               RealtimeBroadcaster realtimeBroadcaster,
                               TaskStatusHistoryRepository taskStatusHistoryRepository,
                               OrganizationMembershipRepository membershipRepository,
                               OrganizationRepository organizationRepository,
                               TeamRepository teamRepository,
                               com.example.taskflow.repository.TaskEvidenceRepository taskEvidenceRepository,
                               ProjectRepository projectRepository,
                               TeamMemberRepository teamMemberRepository,
                               CrewMemberRepository crewMemberRepository) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.roleStrategyFactory = roleStrategyFactory;
        this.taskCommentRepository = taskCommentRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.notificationService = notificationService;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.taskStatusHistoryRepository = taskStatusHistoryRepository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.taskEvidenceRepository = taskEvidenceRepository;
        this.projectRepository = projectRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.crewMemberRepository = crewMemberRepository;
    }

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezoneProperty);
    }

    /**
     * Determines which tasks a user can see based on their Role Strategy.
     * Fix #3: Director/Admin now scoped to their own org. Super Admin retains global view.
     */
    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable) {
        return getTasksForUser(user, pageable, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope) {
        return getTasksForUser(user, pageable, scope, null, null);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId) {
        return getTasksForUser(user, pageable, scope, projectId, null);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope, Long projectId, Long crewId) {
        if (crewId != null) {
            // Validate crew access
            boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId());
            if (!isMember && !roleStrategyFactory.getStrategy(user).canOverride(user)) {
                throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view tasks for this crew.");
            }
            Page<Task> page = taskRepository.findByCrewId(crewId, pageable);
            return batchMapTasks(page);
        }

        if (projectId != null) {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
            
            // Validate project access
            boolean isCreator = project.getCreatedBy() != null && project.getCreatedBy().getId().equals(user.getId());
            boolean inOrg = project.getOrganization() != null && 
                    membershipRepository.existsByUserAndOrganization(user, project.getOrganization());
            boolean inTeam = project.getTeam() != null &&
                    teamMemberRepository.existsByIdTeamIdAndIdUserId(project.getTeam().getId(), user.getId());
            
            if (!isCreator && !inOrg && !inTeam) {
                throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view tasks for this project.");
            }
            
            Page<Task> page = taskRepository.findByProjectId(projectId, pageable);
            return batchMapTasks(page);
        }

        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);

        if (strategy.canOverride(user) || strategy.canViewAllTasks(user)) {
            // Check if this is a SUPER_ADMIN (global view) vs org ADMIN/DIRECTOR (org-scoped view)
            boolean isSuperAdmin = user.getRoles().stream()
                    .anyMatch(r -> {
                        String name = r.getName();
                        if (name.startsWith("ROLE_")) name = name.substring(5);
                        return "SUPER_ADMIN".equals(name);
                    });

            if (isSuperAdmin) {
                // Super Admin: privacy boundary  -  only sees own personal tasks + own crew tasks, not org data
                Page<Task> page = taskRepository.findVisibleForEmployee(user, user.getId(), pageable);
                List<Task> personalOnly = page.stream()
                        .filter(task -> (task.isPersonal() && task.getCreator() != null
                                && task.getCreator().getId().equals(user.getId()))
                                || (task.getCrew() != null))
                        .collect(java.util.stream.Collectors.toList());
                return batchMapList(personalOnly, pageable, personalOnly.size());
            }

            // Director/Admin: org tasks + own personal + crew tasks they belong to
            var memberships = membershipRepository.findByUserId(user.getId());
            if (!memberships.isEmpty()) {
                Long orgId = memberships.get(0).getOrganization().getId();
                Page<Task> page = taskRepository.findByOrganizationIdOrCreatedBy(orgId, user, user.getId(), pageable);
                Page<TaskResponseDTO> result = batchMapTasks(page);
                if (scope != null) {
                    List<TaskResponseDTO> filtered = result.getContent().stream()
                            .filter(dto -> scopeFilter(dto, user, scope))
                            .collect(java.util.stream.Collectors.toList());
                    return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
                }
                return result;
            }
            // Fallback: user is ADMIN/DIRECTOR but somehow has no membership  -  show own tasks only
            Page<Task> page = taskRepository.findByAssigneeOrCreator(user, pageable);
            Page<TaskResponseDTO> fallbackResult = batchMapTasks(page);
            if (scope != null) {
                List<TaskResponseDTO> filtered = fallbackResult.getContent().stream()
                        .filter(dto -> scopeFilter(dto, user, scope))
                        .collect(java.util.stream.Collectors.toList());
                return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
            }
            return fallbackResult;
        }

        if (strategy.canAssign(user)) {
            // Manager: assigned/created + crew tasks (B-12/P1)
            Page<Task> page = taskRepository.findVisibleForManager(user, user.getId(), pageable);
            List<Task> filteredTasks = page.stream()
                    .filter(task -> entityScopeFilter(task, user, scope))
                    .collect(java.util.stream.Collectors.toList());
            return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
        }

        // Employee: own assigned + personal + crew tasks
        Page<Task> page = taskRepository.findVisibleForEmployee(user, user.getId(), pageable);
        List<Task> filteredTasks = page.stream()
                .filter(task -> entityScopeFilter(task, user, scope))
                .collect(java.util.stream.Collectors.toList());
        return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
    }

    /** Scope filter on entity before DTO mapping. personal | org | crew | null(all) */
    private boolean entityScopeFilter(Task task, User user, String scope) {
        if (scope == null) return true;
        if ("personal".equals(scope)) {
            return task.isPersonal() && task.getCreator() != null
                    && task.getCreator().getId().equals(user.getId());
        }
        if ("crew".equals(scope)) {
            return task.getCrew() != null;
        }
        if ("org".equals(scope)) {
            return !task.isPersonal() && task.getCrew() == null;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public TaskResponseDTO getTaskForUser(Long taskId, User user) {
        Task task = getTask(taskId);
        if (!roleStrategyFactory.getStrategy(user).canViewTask(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to view this task.");
        }
        return mapToTaskResponseDTO(task);
    }

    @Transactional
    public TaskResponseDTO submitTask(Long taskId, User user) {
        Task task = getTask(taskId);
        
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        
        // Ensure only the assignee can submit a task for review (assignor/creator cannot submit)
        if (!isAssignee) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the assignee can submit a task for review");
        }

        // B-04c: Personal tasks go To-Do  -  COMPLETED (not ASSIGNED  -  APPROVED)
        if (task.isPersonal()) {
            if (task.getCurrentStatus() == TaskStatus.COMPLETED) {
                return mapToTaskResponseDTO(task); // Already completed  -  no-op
            }
            if (task.getCurrentStatus() != TaskStatus.TODO) {
                throw new IllegalStateException("Only TODO tasks can be completed");
            }
            TaskStatus fromStatus = task.getCurrentStatus();
            task.setCurrentStatus(TaskStatus.COMPLETED);
            Task updated = taskRepository.save(task);
            taskAuditService.recordStatus(updated, fromStatus.name(), "COMPLETED", "COMPLETED", user, "Personal task marked complete");
            notificationService.createAndSend(task.getCreator(), user, com.example.taskflow.notification.NotificationEvent.TASK_APPROVED,
                "Task Completed", "Personal task " + task.getTitle() + " has been completed.", updated, "task-completed:" + task.getId(), user);
            TaskResponseDTO dto = mapToTaskResponseDTO(updated);
            realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
            return dto;
        }

        // Spec: crew tasks have NO review pipeline. Use /complete-crew instead.
        if (task.getCrew() != null) {
            throw new IllegalStateException("Crew tasks do not have a review pipeline. Use /complete-crew to complete a crew task.");
        }

        // Check dependencies  -  treat APPROVED (org) or COMPLETED (personal/crew) as done
        boolean hasIncompleteDependencies = taskDependencyRepository.findByTask_Id(taskId).stream()
            .map(TaskDependency::getDependsOn)
            .anyMatch(blockingTask -> {
                TaskStatus s = blockingTask.getCurrentStatus();
                return s != TaskStatus.APPROVED && s != TaskStatus.COMPLETED;
            });

        if (hasIncompleteDependencies) {
            throw new IllegalStateException("Cannot submit task. One or more dependencies are not completed.");
        }

        if (task.getCurrentStatus() != TaskStatus.IN_PROGRESS && task.getCurrentStatus() != TaskStatus.REJECTED) {
            throw new IllegalStateException("Only ASSIGNED or REJECTED tasks can be submitted");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.SUBMITTED);
        task.setRejectionReason(null);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "SUBMITTED", "SUBMITTED", user, null);
        
        // Notify creator
        notificationService.createAndSend(task.getCreator(), user, com.example.taskflow.notification.NotificationEvent.TASK_SUBMITTED,
            "Task Submitted", "Task " + task.getTitle() + " has been submitted for review.", updated, "task-submitted:" + task.getId(), user);
            
        // Broadcast real-time update
        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        
        return dto;
    }

    @Transactional
    public TaskResponseDTO approveTask(Long taskId, User reviewer) {
        Task task = getTask(taskId);
        RoleStrategy strategy = roleStrategyFactory.getStrategy(reviewer);

        validateReviewer(reviewer, task);

        // Defensive: crew tasks should never reach SUBMITTED, but guard anyway
        if (task.getCrew() != null) {
            throw new IllegalStateException("Crew tasks cannot be approved. Use /complete-crew instead.");
        }

        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be approved");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.APPROVED);
        task.setReviewer(reviewer); // Store who approved it
        task.setRejectionReason(null); // Defensive clear
        
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "APPROVED", "APPROVED", reviewer, null);
        
        notificationService.createAndSend(task.getAssignee(), reviewer, com.example.taskflow.notification.NotificationEvent.TASK_APPROVED,
            "Task Approved", "Your task " + task.getTitle() + " was approved.", updated, "task-approved:" + task.getId(), reviewer);
            
        List<TaskDependency> dependents = taskDependencyRepository.findByDependsOn_Id(taskId);
        for (TaskDependency dep : dependents) {
            Task blockedTask = dep.getTask();
            if (blockedTask.getAssignee() == null) continue;
            if (blockedTask.getAssignee().getId().equals(reviewer.getId())) continue;  // self-exclusion
            notificationService.createAndSend(
                blockedTask.getAssignee(),
                reviewer,
                com.example.taskflow.notification.NotificationEvent.DEPENDENCY_RESOLVED,
                "Dependency resolved: " + task.getTitle(),
                "A blocking task has been approved. You can now submit.",
                blockedTask,
                "dep-resolved:" + taskId + ":" + blockedTask.getId(),
                reviewer
            );
        }

        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        
        return dto;
    }

    @Transactional
    public TaskResponseDTO rejectTask(Long taskId, User reviewer, String reason) {
        Task task = getTask(taskId);
        validateReviewer(reviewer, task);

        // Defensive: crew tasks should never reach SUBMITTED, but guard anyway
        if (task.getCrew() != null) {
            throw new IllegalStateException("Crew tasks cannot be rejected. They have no review pipeline.");
        }

        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be rejected");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.REJECTED);
        task.setReviewer(reviewer);
        task.setRejectionReason(reason);
        
        User formerAssignee = task.getAssignee();
        
        // Bug #2 Fix: Always clear assignee so it consistently enters the backlog state
        task.setAssignee(null);
        
        Task updated = taskRepository.save(task);

        // Note(frontend): migration - frontend needs to read rejection reason from /dashboard/activity/task/{taskId} instead of comments
        taskAuditService.recordStatus(updated, fromStatus.name(), "REJECTED", "REJECTED", reviewer, reason);
        
        if (formerAssignee != null) {
            notificationService.createAndSend(formerAssignee, reviewer, com.example.taskflow.notification.NotificationEvent.TASK_REJECTED,
                "Task Rejected", "Your task " + task.getTitle() + " was rejected. Reason: " + reason + ". It has been returned to the team backlog.", updated, "task-rejected:" + task.getId(), reviewer);
        }
            
        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        
        return dto;
    }

    // --- Helper Methods ---

    /** B-05: Scope filter for getTasksForUser (DTO-level, for admin/director paths) */
    private boolean scopeFilter(TaskResponseDTO dto, User user, String scope) {
        if (scope == null) return true;
        if ("personal".equals(scope)) {
            return dto.isPersonal() && user.getUsername().equals(dto.getCreator());
        }
        if ("crew".equals(scope)) {
            return dto.getCrewId() != null;
        }
        if ("org".equals(scope)) {
            return !dto.isPersonal() && dto.getCrewId() == null;
        }
        return true;
    }

    private void validateReviewer(User reviewer, Task task) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(reviewer);
        if (!strategy.canReview(reviewer, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to review this task.");
        }
        // Spec: the assignor (creator) CAN review  -  only the assignee is
        // blocked from self-review. EmployeeStrategy.canReview already
        // enforces this (assignee != reviewer), so no creator block here.
        // Org boundary check: reviewer must be in the same org as the task
        if (task.getOrg() != null) {
            boolean inSameOrg = membershipRepository.existsByUserAndOrganization(reviewer, task.getOrg());
            if (!inSameOrg) {
                throw new com.example.taskflow.exception.UnauthorizedActionException("You cannot review tasks outside your organization.");
            }
        }
    }

    private Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new com.example.taskflow.exception.TaskNotFoundException("Task not found with ID: " + taskId));
    }

    // --- Batch DTO Mapping (avoids N+1 on dependencies) ---
    private Page<TaskResponseDTO> batchMapTasks(Page<Task> page) {
        List<Long> taskIds = page.getContent().stream().map(Task::getId).collect(java.util.stream.Collectors.toList());
        if (taskIds.isEmpty()) return Page.empty();

        java.util.Map<Long, List<TaskDependency>> blockingByTaskId = taskDependencyRepository.findAllByTaskIdIn(taskIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getTask().getId()));
        java.util.Map<Long, List<TaskDependency>> blockedByByTaskId = taskDependencyRepository.findAllByDependsOnIdIn(taskIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getDependsOn().getId()));

        List<TaskResponseDTO> dtos = page.getContent().stream()
                .map(task -> mapToTaskResponseDTO(task, blockingByTaskId, blockedByByTaskId))
                .collect(java.util.stream.Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(dtos, page.getPageable(), page.getTotalElements());
    }

    private Page<TaskResponseDTO> batchMapList(java.util.List<Task> tasks, Pageable pageable, long total) {
        if (tasks.isEmpty()) return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, 0);
        List<Long> taskIds = tasks.stream().map(Task::getId).collect(java.util.stream.Collectors.toList());

        java.util.Map<Long, List<TaskDependency>> blockingByTaskId = taskDependencyRepository.findAllByTaskIdIn(taskIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getTask().getId()));
        java.util.Map<Long, List<TaskDependency>> blockedByByTaskId = taskDependencyRepository.findAllByDependsOnIdIn(taskIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getDependsOn().getId()));

        List<TaskResponseDTO> dtos = tasks.stream()
                .map(task -> mapToTaskResponseDTO(task, blockingByTaskId, blockedByByTaskId))
                .collect(java.util.stream.Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, total);
    }

    // --- DTO Mapping Methods ---
    private TaskResponseDTO mapToTaskResponseDTO(Task task) {
        return mapToTaskResponseDTO(task, null, null);
    }

    /** Batch-aware mapper  -  pass pre-fetched dependency maps to avoid N+1 */
    private TaskResponseDTO mapToTaskResponseDTO(Task task,
            java.util.Map<Long, List<TaskDependency>> blockingByTaskId,
            java.util.Map<Long, List<TaskDependency>> blockedByByTaskId) {
        Long taskId = task.getId();
        List<TaskDependency> blocking = (blockingByTaskId != null)
            ? blockingByTaskId.getOrDefault(taskId, java.util.Collections.emptyList())
            : taskDependencyRepository.findByTask_Id(taskId);
        List<TaskDependency> blockedBy = (blockedByByTaskId != null)
            ? blockedByByTaskId.getOrDefault(taskId, java.util.Collections.emptyList())
            : taskDependencyRepository.findByDependsOn_Id(taskId);
        
        List<TaskSummaryDTO> blocksDto = blocking.stream()
            .map(d -> new TaskSummaryDTO(d.getDependsOn().getId(), d.getDependsOn().getTitle(), d.getDependsOn().getCurrentStatus()))
            .collect(java.util.stream.Collectors.toList());
            
        List<TaskSummaryDTO> blockedByDto = blockedBy.stream()
            .map(d -> new TaskSummaryDTO(d.getTask().getId(), d.getTask().getTitle(), d.getTask().getCurrentStatus()))
            .collect(java.util.stream.Collectors.toList());

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
            task.getChecklists() != null ? task.getChecklists().stream().map(this::mapToChecklistItemDTO).collect(java.util.stream.Collectors.toList()) : java.util.Collections.emptyList(),
            blocksDto,
            blockedByDto,
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

    private TaskCommentDTO mapToTaskCommentDTO(TaskComment comment) {
        return new TaskCommentDTO(
            comment.getId(),
            comment.getAuthor().getUsername(),
            comment.getComment(),
            comment.getParent() != null ? comment.getParent().getId() : null,
            java.util.Collections.emptyList(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }

    private TaskCommentDTO mapToTaskCommentDTOWithReplies(TaskComment comment) {
        List<TaskCommentDTO> replies = comment.getReplies() != null
                ? comment.getReplies().stream().map(this::mapToTaskCommentDTO).collect(java.util.stream.Collectors.toList())
                : java.util.Collections.emptyList();
        return new TaskCommentDTO(
            comment.getId(),
            comment.getAuthor().getUsername(),
            comment.getComment(),
            comment.getParent() != null ? comment.getParent().getId() : null,
            replies,
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }

    private ChecklistItemDTO mapToChecklistItemDTO(ChecklistItem item) {
        return new ChecklistItemDTO(
            item.getId(),
            item.getText(),
            item.getIsCompleted(),
            item.getDisplayOrder(),
            item.getCreatedBy() != null ? item.getCreatedBy().getUsername() : null
        );
    }

    // --- Comments & Checklists ---

    @Transactional
    public TaskCommentDTO addComment(Long taskId, User user, String text) {
        return addComment(taskId, user, text, null);
    }

    @Transactional
    public TaskCommentDTO addComment(Long taskId, User user, String text, Long parentId) {
        Task task = getTask(taskId);

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(user);
        comment.setComment(text);
        comment.setCreatedAt(LocalDateTime.now());

        if (parentId != null) {
            TaskComment parent = taskCommentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found: " + parentId));
            if (!parent.getTask().getId().equals(taskId)) {
                throw new IllegalArgumentException("Parent comment does not belong to this task");
            }
            comment.setParent(parent);
        }

        TaskComment saved = taskCommentRepository.save(comment);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "COMMENTED", user, null,
                java.util.Map.of("comment", text, "parentId", parentId != null ? parentId : "null"));
        
        Set<User> participants = new HashSet<>();
        if (task.getAssignee() != null) participants.add(task.getAssignee());
        if (task.getCreator() != null) participants.add(task.getCreator());
        if (task.getReviewer() != null) participants.add(task.getReviewer());
        
        for (User p : participants) {
            notificationService.createAndSend(p, user, com.example.taskflow.notification.NotificationEvent.TASK_COMMENTED,
                "New Comment", user.getUsername() + " commented on task: " + task.getTitle(), task, "task-commented:" + task.getId() + ":" + user.getId(), user);
        }
        
        return mapToTaskCommentDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<TaskCommentDTO> getComments(Long taskId, User user, Pageable pageable) {
        getTask(taskId); // ensure exists
        // Return top-level comments only; replies are nested under parent
        return taskCommentRepository.findByTaskIdAndParentIsNullOrderByCreatedAtAsc(taskId, pageable)
                .map(this::mapToTaskCommentDTOWithReplies);
    }


    @Transactional
    public ChecklistItemDTO addChecklistItem(Long taskId, String text, User user) {
        Task task = getTask(taskId);

        ChecklistItem item = new ChecklistItem();
        item.setTask(task);
        item.setText(text);
        item.setIsCompleted(false);
        item.setCreatedBy(user);
        ChecklistItem saved = checklistItemRepository.save(item);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "CHECKLIST_ADDED", user, text, java.util.Map.of("checklistItem", text));
        return mapToChecklistItemDTO(saved);
    }

    @Transactional
    public ChecklistItemDTO toggleChecklistItem(Long taskId, Long itemId, User user) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new com.example.taskflow.exception.TaskNotFoundException("Checklist item not found"));
        
        if (!item.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Checklist item does not belong to this task");
        }
        
        // B-11: Allow both the creator AND the task assignee to toggle checklist items
        boolean isCreator = item.getCreatedBy() != null && item.getCreatedBy().getId().equals(user.getId());
        boolean isAssignee = item.getTask().getAssignee() != null && item.getTask().getAssignee().getId().equals(user.getId());
        if (!isCreator && !isAssignee) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the creator or assignee of the checklist item can modify it.");
        }

        item.setIsCompleted(!item.getIsCompleted());
        // ER-M03 fix: stamp completed_at when is_completed flips to true,
        // clear it when flipping back to false. The column was added by V39
        // migration; the entity field already existed but was never written.
        if (item.getIsCompleted()) {
            item.setCompletedAt(LocalDateTime.now());
        } else {
            item.setCompletedAt(null);
        }
        ChecklistItem saved = checklistItemRepository.save(item);
        taskAuditService.recordStatus(item.getTask(), item.getTask().getCurrentStatus().name(), item.getTask().getCurrentStatus().name(), "CHECKLIST_TOGGLED", user, item.getText() + " completed: " + item.getIsCompleted(), java.util.Map.of("itemId", item.getId(), "completed", item.getIsCompleted()));
        
        boolean allComplete = checklistItemRepository.findByTaskIdOrderByDisplayOrderAsc(taskId).stream().allMatch(ChecklistItem::getIsCompleted);
        if (allComplete) {
            notificationService.createAndSend(item.getTask().getCreator(), user, com.example.taskflow.notification.NotificationEvent.CHECKLIST_UPDATED,
                "Checklist Completed", "All items completed on task: " + item.getTask().getTitle(), item.getTask(), "checklist-updated:" + item.getTask().getId(), user);
        }
        
        return mapToChecklistItemDTO(saved);
    }

    @Transactional
    public void deleteChecklistItem(Long taskId, Long itemId, User user) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new com.example.taskflow.exception.TaskNotFoundException("Checklist item not found"));
        
        if (!item.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Checklist item does not belong to this task");
        }

        if (item.getCreatedBy() != null && !item.getCreatedBy().getId().equals(user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the creator of the checklist item can delete it.");
        }

        checklistItemRepository.delete(item);
        taskAuditService.recordStatus(item.getTask(), item.getTask().getCurrentStatus().name(), item.getTask().getCurrentStatus().name(), "CHECKLIST_REMOVED", user, item.getText(), java.util.Map.of("itemId", item.getId(), "text", item.getText()));
    }

    @Transactional
    public void reorderChecklistItems(Long taskId, List<Long> itemIds, User user) {
        Task task = getTask(taskId);
        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);

        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            ChecklistItem item = checklistItemRepository.findById(itemId).orElse(null);
            if (item != null && item.getTask().getId().equals(taskId)) {
                // Only the creator or a user with override (DIRECTOR/ADMIN/SUPER_ADMIN) can reorder
                boolean isCreator = item.getCreatedBy() != null && item.getCreatedBy().getId().equals(user.getId());
                if (!isCreator && !strategy.canOverride(user)) {
                    throw new com.example.taskflow.exception.UnauthorizedActionException("Only the creator of checklist item '" + item.getText() + "' can reorder it.");
                }
                item.setDisplayOrder(i);
                checklistItemRepository.save(item);
            }
        }
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "CHECKLIST_REORDERED", user, null, java.util.Map.of("itemIds", itemIds));
    }

    // --- Dependencies ---
    @Transactional
    public void addDependency(Long taskId, Long dependsOnId, User user) {
        Task task = getTask(taskId);
        Task dependsOnTask = getTask(dependsOnId);

        if (task.getId().equals(dependsOnTask.getId())) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }

        // Validate workspaces match
        if (task.getOrg() != null || dependsOnTask.getOrg() != null) {
            if (task.getOrg() == null || dependsOnTask.getOrg() == null) {
                throw new IllegalArgumentException("Cannot mix organization tasks with personal/crew tasks in dependencies");
            }
            if (!task.getOrg().getId().equals(dependsOnTask.getOrg().getId())) {
                throw new IllegalArgumentException("Cannot add dependency between tasks in different organizations");
            }
        } else if (task.getCrew() != null || dependsOnTask.getCrew() != null) {
            if (task.getCrew() == null || dependsOnTask.getCrew() == null) {
                throw new IllegalArgumentException("Cannot mix crew tasks with personal tasks in dependencies");
            }
            if (!task.getCrew().getId().equals(dependsOnTask.getCrew().getId())) {
                throw new IllegalArgumentException("Cannot add dependency between tasks in different crews");
            }
        } else {
            // Both are personal tasks
            if (task.getCreator() == null || dependsOnTask.getCreator() == null || !task.getCreator().getId().equals(dependsOnTask.getCreator().getId())) {
                throw new IllegalArgumentException("Cannot add dependency between personal tasks of different users");
            }
        }

        if (taskDependencyRepository.existsByTask_IdAndDependsOn_Id(taskId, dependsOnId)) {
            throw new IllegalArgumentException("Dependency already exists");
        }

        // Cycle Detection (DFS)
        if (detectCycle(dependsOnTask, task, new HashSet<>())) {
            throw new IllegalStateException("Cannot add dependency: It would create a circular dependency loop.");
        }

        TaskDependency dependency = new TaskDependency();
        dependency.setId(new com.example.taskflow.domain.TaskDependencyId(taskId, dependsOnId));
        dependency.setTask(task);
        dependency.setDependsOn(dependsOnTask);
        dependency.setCreatedBy(user);
        try {
            taskDependencyRepository.saveAndFlush(dependency);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Dependency already exists");
        }
        
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "DEPENDENCY_ADDED", user, "Blocked by task " + dependsOnId, java.util.Map.of("dependsOnId", dependsOnId));
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(user.getId())) {
            notificationService.createAndSend(
                task.getAssignee(),
                user,
                com.example.taskflow.notification.NotificationEvent.TASK_BLOCKED,
                "Task Blocked",
                "Your task has been blocked by another task: " + dependsOnTask.getTitle(),
                task,
                "task-blocked:" + task.getId() + ":" + dependsOnTask.getId(),
                user
            );
        }
    }

    private boolean detectCycle(Task current, Task target, Set<Long> visited) {
        if (current.getId().equals(target.getId())) {
            return true; // We found a path back to the target!
        }

        if (visited.contains(current.getId())) {
            return false;
        }

        visited.add(current.getId());

        // Find what current is blocked by
        return taskDependencyRepository.findByTask_Id(current.getId()).stream()
                .map(TaskDependency::getDependsOn)
                .anyMatch(nextTask -> detectCycle(nextTask, target, visited));
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependsOnId, User user) {
        Task task = getTask(taskId);
        
        com.example.taskflow.domain.TaskDependencyId depKey = new com.example.taskflow.domain.TaskDependencyId(taskId, dependsOnId);
        TaskDependency dependency = taskDependencyRepository.findById(depKey)
                .orElseThrow(() -> new IllegalArgumentException("Dependency not found"));
        
        taskDependencyRepository.delete(dependency);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "DEPENDENCY_REMOVED", user, "Dependency removed: depends_on=" + dependsOnId, java.util.Map.of("dependsOnId", dependsOnId));
    }

    @Transactional
    public TaskResponseDTO updateTask(Long taskId, TaskUpdateRequestDTO request, User user) {
        Task task = getTask(taskId);
        
        if (!roleStrategyFactory.getStrategy(user).canEdit(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to edit this task.");
        }

        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getDueDate() != null) {
            if (request.getDueDate().isBefore(java.time.LocalDate.now(zoneId))) {
                throw new IllegalArgumentException("Due date cannot be in the past");
            }
            task.setDueDate(request.getDueDate());
        }
        if (request.getTags() != null) task.setTags(request.getTags());

        // B-15: Allow status updates for personal tasks only
        if (request.getStatus() != null) {
            if (task.isPersonal()) {
                if (request.getStatus() != TaskStatus.TODO && request.getStatus() != TaskStatus.COMPLETED) {
                    throw new IllegalArgumentException("Personal tasks can only be set to TODO or COMPLETED");
                }
                task.setCurrentStatus(request.getStatus());
            } else {
                throw new IllegalArgumentException("Status can only be updated for personal tasks");
            }
        }
        
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(), "UPDATED", user, "Task details updated", java.util.Map.of("priority", request.getPriority() != null ? request.getPriority().name() : "none"));
        return mapToTaskResponseDTO(updated);
    }

    @Transactional
    public void deleteTask(Long taskId, User user) {
        Task task = getTask(taskId);

        if (!roleStrategyFactory.getStrategy(user).canDelete(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to delete this task.");
        }
        // Audit BEFORE delete
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), "DELETED", "DELETED", user, "Task deleted");

        // Delete related rows
        taskStatusHistoryRepository.deleteByTaskId(taskId);
        taskCommentRepository.deleteByTaskId(taskId);
        taskDependencyRepository.deleteByTaskId(taskId);
        taskDependencyRepository.deleteByDependsOnId(taskId);
        checklistItemRepository.deleteByTaskId(taskId);
        taskEvidenceRepository.deleteByTask_Id(taskId);

        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponseDTO toggleArchive(Long taskId, User user) {
        Task task = getTask(taskId);
        
        if (!roleStrategyFactory.getStrategy(user).canArchive(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to archive this task.");
        }

        task.setArchived(!task.isArchived());
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(),
            updated.isArchived() ? "ARCHIVED" : "UNARCHIVED", user, null, java.util.Map.of("archived", updated.isArchived()));
        return mapToTaskResponseDTO(updated);
    }

    @Transactional
    public TaskResponseDTO reassignTask(Long taskId, User newAssignee, User user) {
        Task task = getTask(taskId);
        
        if (!roleStrategyFactory.getStrategy(user).canReassign(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to reassign this task.");
        }

        if (task.getCurrentStatus() == TaskStatus.APPROVED || task.getCurrentStatus() == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Completed or approved tasks cannot be reassigned.");
        }
        
        if (task.getOrg() != null) {
            OrganizationMembership reassignerMembership = membershipRepository.findByUserAndOrganization(user, task.getOrg())
                    .orElseThrow(() -> new UnauthorizedActionException("You are not a member of the task's organization."));
                    
            OrganizationMembership assigneeMembership = membershipRepository.findByUserAndOrganization(newAssignee, task.getOrg())
                    .orElseThrow(() -> new IllegalArgumentException("Assignee must be a member of the task's organization."));

            // Enforce Role Priority Assignment Guard (0 is top, lower priority value = higher power)
            if (reassignerMembership.getOrgRole() != null && assigneeMembership.getOrgRole() != null) {
                Integer reassignerPriority = reassignerMembership.getOrgRole().getPriority();
                Integer assigneePriority = assigneeMembership.getOrgRole().getPriority();
                
                int rPriority = reassignerPriority != null ? reassignerPriority : 100;
                int aPriority = assigneePriority != null ? assigneePriority : 100;
                
                // If both are 100 or something, we shouldn't block equal priority if they are just peers. 
                // Wait, if they are equal priority, can they assign to each other? The logic says rPriority >= aPriority blocks it.
                // If they are on the same level (e.g. MANAGER assigning to MANAGER), it throws?
                // Let's keep the existing logic but fix NPE. 
                if (rPriority >= aPriority) {
                    throw new IllegalArgumentException("You do not have enough power (role priority) to assign tasks to this user.");
                }
            }
        }
        if (task.getTeam() != null) {
            boolean isTeamMember = task.getTeam().getMembers().stream()
                    .anyMatch(m -> m.getId().equals(newAssignee.getId()));
            if (!isTeamMember) {
                throw new IllegalArgumentException("Assignee is not a member of team: " + task.getTeam().getName());
            }
        }
        
        if (task.getProject() != null && task.getProject().getTeam() != null) {
            boolean isProjectTeamMember = task.getProject().getTeam().getMembers().stream()
                    .anyMatch(m -> m.getId().equals(newAssignee.getId()));
            if (!isProjectTeamMember) {
                throw new IllegalArgumentException("Assignee is not a member of the project's team");
            }
        }
        
        User oldAssignee = task.getAssignee();
        TaskStatus oldStatus = task.getCurrentStatus();
        
        task.setAssignee(newAssignee);
        task.setCurrentStatus(task.isPersonal() ? TaskStatus.TODO : (task.getCrew() != null ? TaskStatus.TODO : TaskStatus.IN_PROGRESS)); // RESET
        task.setReviewer(null); // RESET
        task.setRejectionReason(null);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, oldStatus.name(), updated.getCurrentStatus().name(), "REASSIGNED", user, "Reassigned to " + newAssignee.getUsername(), java.util.Map.of("assigneeFrom", oldAssignee != null ? oldAssignee.getUsername() : null, "assigneeTo", newAssignee.getUsername()));
        
        if (oldAssignee != null) {
            notificationService.createAndSend(oldAssignee, user, com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
                "Task Reassigned", "Task " + task.getTitle() + " has been reassigned to " + newAssignee.getUsername(), updated, "task-unassigned:" + task.getId() + ":" + oldAssignee.getId(), user);
        }
        
        notificationService.createAndSend(newAssignee, user, com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
            "You have a new task: " + task.getTitle(), "Task has been assigned to you.", updated, "task-assigned:" + task.getId() + ":" + newAssignee.getId(), user);
            
        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        
        return dto;
    }

    @Transactional
    public TaskResponseDTO completePersonalTask(Long taskId, User user) {
        Task task = getTask(taskId);
        if (!task.isPersonal()) {
            throw new IllegalArgumentException("Only personal tasks can be completed directly");
        }
        if (!task.getCreator().getId().equals(user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the owner can complete a personal task");
        }
        if (task.getCurrentStatus() == TaskStatus.COMPLETED) {
            return mapToTaskResponseDTO(task); // Already completed
        }
        if (task.getCurrentStatus() != TaskStatus.TODO) {
            throw new IllegalStateException("Only TODO personal tasks can be completed");
        }
        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.COMPLETED);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "COMPLETED", "COMPLETED", user, "Personal task marked complete");
        notificationService.createAndSend(task.getCreator(), user, com.example.taskflow.notification.NotificationEvent.TASK_APPROVED,
            "Task Completed", "Personal task " + task.getTitle() + " has been completed.", updated, "task-completed:" + task.getId(), user);
        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    /**
     * SM-M03 fix: Recall a submitted task back to ASSIGNED.
     * Spec state machine: "SUBMITTED --> ASSIGNED : assignee recalls".
     * Only the assignee can recall; task must be in SUBMITTED status.
     */
    @Transactional
    public TaskResponseDTO recallTask(Long taskId, User user) {
        Task task = getTask(taskId);

        // Only the assignee can recall their own submission
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        if (!isAssignee) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the assignee can recall a submitted task.");
        }

        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be recalled.");
        }

        // Personal tasks never enter SUBMITTED, so this is implicitly org-only.
        // Crew tasks have no review pipeline, so they never enter SUBMITTED either.
        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.IN_PROGRESS);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "IN_PROGRESS", "RECALLED", user,
                "Assignee recalled submission", java.util.Map.of("recalledBy", user.getUsername()));

        // Notify the creator that the submission was recalled
        if (task.getCreator() != null && !task.getCreator().getId().equals(user.getId())) {
            notificationService.createAndSend(task.getCreator(), user,
                com.example.taskflow.notification.NotificationEvent.TASK_SUBMITTED,
                "Task Recalled",
                "Task " + task.getTitle() + " was recalled by " + user.getUsername() + ".",
                updated, "task-recalled:" + task.getId(), user);
        }

        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    /**
     * Complete a crew task. Supports both:
     *   - ASSIGNED -> COMPLETED (task was claimed/nudged, now completed)
     *   - TODO -> COMPLETED (implicit claim + complete in one step)
     * Spec: any crew member can complete any crew task (flat structure).
     */
    @Transactional
    public TaskResponseDTO completeCrewTask(Long taskId, User user) {
        Task task = getTask(taskId);

        if (task.getCrew() == null) {
            throw new IllegalArgumentException("This endpoint is only for crew tasks. Use /complete for personal tasks or /approve for org tasks.");
        }

        // Any crew member can complete a crew task (flat structure)
        // The @PreAuthorize("hasPermission(..., 'EDIT')") on the controller
        // already verified via EmployeeStrategy.canEdit that the caller is a
        // crew member. No additional check needed here.

        if (task.getCurrentStatus() == TaskStatus.COMPLETED) {
            return mapToTaskResponseDTO(task); // Already completed - no-op
        }
        if (task.getCurrentStatus() != TaskStatus.IN_PROGRESS && task.getCurrentStatus() != TaskStatus.TODO) {
            throw new IllegalStateException("Only TODO or ASSIGNED crew tasks can be completed. Current status: " + task.getCurrentStatus());
        }

        TaskStatus fromStatus = task.getCurrentStatus();

        // If completing from TODO (unclaimed), auto-set assignee to the completer
        // (implicit claim + complete in one step)
        if (fromStatus == TaskStatus.TODO && task.getAssignee() == null) {
            task.setAssignee(user);
        }

        task.setCurrentStatus(TaskStatus.COMPLETED);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "COMPLETED", "COMPLETED", user,
                "Crew task marked complete by " + user.getUsername());

        // Notify all crew members (except the completer) that the task is done
        try {
            if (task.getCrew() != null && task.getCrew().getMembers() != null) {
                for (com.example.taskflow.domain.CrewMember cm : task.getCrew().getMembers()) {
                    User member = cm.getUser();
                    if (member != null && !member.getId().equals(user.getId())) {
                        notificationService.createAndSend(member, user,
                            com.example.taskflow.notification.NotificationEvent.TASK_APPROVED,
                            "Crew Task Completed",
                            "Crew task " + task.getTitle() + " has been completed by " + user.getUsername() + ".",
                            updated, "crew-task-completed:" + task.getId() + ":" + member.getId(), user);
                    }
                }
            }
        } catch (Exception e) {
            // Best-effort notification - do not fail the completion
            org.slf4j.LoggerFactory.getLogger(TaskWorkflowService.class)
                .warn("Failed to notify crew members of task completion: {}", e.getMessage());
        }

        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    /**
     * Claim an unclaimed crew task. Transitions TODO -> ASSIGNED.
     * Spec: crew tasks use a claiming model  -  anyone creates a task,
     * anyone claims it (first-taker wins via @Version optimistic lock).
     */
    @Transactional
    public TaskResponseDTO claimTask(Long taskId, User user) {
        Task task = getTask(taskId);

        if (task.getCrew() == null) {
            throw new IllegalArgumentException("Only crew tasks can be claimed. Org tasks are IN_PROGRESS, personal tasks are self-owned.");
        }

        if (task.getCurrentStatus() != TaskStatus.TODO) {
            throw new IllegalStateException("Only unclaimed (TODO) crew tasks can be claimed. Current status: " + task.getCurrentStatus());
        }

        if (task.getAssignee() != null) {
            throw new IllegalStateException("This task has already been claimed by " + task.getAssignee().getUsername());
        }

        // Verify the claimer is a crew member
        if (!task.getCrew().getMembers().stream()
                .anyMatch(cm -> cm.getUser().getId().equals(user.getId()))) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You must be a member of this crew to claim a task.");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setAssignee(user);
        task.setCurrentStatus(TaskStatus.IN_PROGRESS);
        
        Task updated;
        try {
            updated = taskRepository.save(task); // @Version provides optimistic lock (first-taker wins)
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new IllegalStateException("Task already claimed");
        }

        taskAuditService.recordStatus(updated, fromStatus.name(), "IN_PROGRESS", "CLAIMED", user,
                "Task claimed by " + user.getUsername(),
                java.util.Map.of("claimedBy", user.getUsername()));

        // Notify the task creator that someone claimed it
        if (task.getCreator() != null && !task.getCreator().getId().equals(user.getId())) {
            notificationService.createAndSend(task.getCreator(), user,
                com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
                "Task Claimed",
                "Crew task " + task.getTitle() + " was claimed by " + user.getUsername() + ".",
                updated, "crew-task-claimed:" + task.getId(), user);
        }

        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }
}
