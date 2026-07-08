package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.TaskStatus;

import com.example.taskflow.domain.Attachment;
import com.example.taskflow.domain.ChecklistItem;
import com.example.taskflow.repository.AttachmentRepository;
import com.example.taskflow.repository.TaskStatusHistoryRepository;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskComment;
import com.example.taskflow.domain.TaskDependency;
import com.example.taskflow.domain.User;
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
    private final AttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;

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
                               AttachmentRepository attachmentRepository,
                               FileStorageService fileStorageService,
                               OrganizationMembershipRepository membershipRepository,
                               OrganizationRepository organizationRepository,
                               TeamRepository teamRepository) {
        this.taskRepository = taskRepository;
        this.taskAuditService = taskAuditService;
        this.roleStrategyFactory = roleStrategyFactory;
        this.taskCommentRepository = taskCommentRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.notificationService = notificationService;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.taskStatusHistoryRepository = taskStatusHistoryRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileStorageService = fileStorageService;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
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
        return getTasksForUser(user, pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getTasksForUser(User user, Pageable pageable, String scope) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(user);

        if (strategy.canOverride(user)) {
            // Check if this is a SUPER_ADMIN (global view) vs org ADMIN/DIRECTOR (org-scoped view)
            boolean isSuperAdmin = user.getRoles().stream()
                    .anyMatch(r -> {
                        String name = r.getName();
                        if (name.startsWith("ROLE_")) name = name.substring(5);
                        return "SUPER_ADMIN".equals(name);
                    });

            if (isSuperAdmin) {
                // Super Admin: platform-wide view (read-only per Rule 8), with scope filter
                Page<Task> page = taskRepository.findAll(pageable);
                Page<TaskResponseDTO> result = batchMapTasks(page);
                if (scope != null) {
                    List<TaskResponseDTO> filtered = result.getContent().stream()
                            .filter(dto -> scopeFilter(dto, user, scope))
                            .collect(java.util.stream.Collectors.toList());
                    return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
                }
                return result;
            }

            // Director/Admin: only see tasks within their org + their own personal tasks
            var memberships = membershipRepository.findByUserId(user.getId());
            if (!memberships.isEmpty()) {
                Long orgId = memberships.get(0).getOrganization().getId();
                Page<Task> page = taskRepository.findByOrganizationIdOrCreatedBy(orgId, user, pageable);
                Page<TaskResponseDTO> result = batchMapTasks(page);
                if (scope != null) {
                    List<TaskResponseDTO> filtered = result.getContent().stream()
                            .filter(dto -> scopeFilter(dto, user, scope))
                            .collect(java.util.stream.Collectors.toList());
                    return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
                }
                return result;
            }
            // Fallback: user is ADMIN/DIRECTOR but somehow has no membership — show own tasks only
            Page<Task> page = taskRepository.findByAssignedToOrCreatedBy(user, pageable);
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
            // Manager: see assigned/created tasks (B-12: personal-task filter pushed to DB query)
            Page<Task> page = taskRepository.findVisibleForManager(user, pageable);
            List<Task> filteredTasks = page.stream()
                    .filter(task -> {
                        // B-05: Scope filter
                        if ("personal".equals(scope)) return task.isPersonal() && task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
                        if ("org".equals(scope)) return !task.isPersonal();
                        return true;
                    })
                    .collect(java.util.stream.Collectors.toList());
            return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
        }

        // Employee: see only own assigned tasks + own personal tasks (B-12: personal filter in DB)
        Page<Task> page = taskRepository.findVisibleForEmployee(user, pageable);
        List<Task> filteredTasks = page.stream()
                .filter(task -> {
                    // B-05: Scope filter
                    if ("personal".equals(scope)) return task.isPersonal() && task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
                    if ("org".equals(scope)) return !task.isPersonal();
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());
        return batchMapList(filteredTasks, pageable, scope != null ? filteredTasks.size() : page.getTotalElements());
    }

    @Transactional
    public TaskResponseDTO submitTask(Long taskId, User user) {
        Task task = getTask(taskId);
        
        // Ensure only the assignee can submit
        if (!task.getAssignedTo().getId().equals(user.getId())) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the assignee can submit their task");
        }

        // B-04c: Personal tasks go To-Do → COMPLETED (not ASSIGNED → APPROVED)
        if (task.isPersonal()) {
            if (task.getCurrentStatus() == TaskStatus.COMPLETED) {
                return mapToTaskResponseDTO(task); // Already completed — no-op
            }
            if (task.getCurrentStatus() != TaskStatus.TODO) {
                throw new IllegalStateException("Only TODO tasks can be completed");
            }
            TaskStatus fromStatus = task.getCurrentStatus();
            task.setCurrentStatus(TaskStatus.COMPLETED);
            Task updated = taskRepository.save(task);
            taskAuditService.recordStatus(updated, fromStatus.name(), "COMPLETED", "COMPLETED", user, "Personal task marked complete");
            notificationService.createAndSend(task.getCreatedBy(), user, com.example.taskflow.notification.NotificationEvent.TASK_APPROVED,
                "Task Completed", "Personal task " + task.getTitle() + " has been completed.", updated, "task-completed:" + task.getId());
            TaskResponseDTO dto = mapToTaskResponseDTO(updated);
            realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
            return dto;
        }

        // Check dependencies
        boolean hasIncompleteDependencies = taskDependencyRepository.findByTask_Id(taskId).stream()
            .map(TaskDependency::getBlocksTask)
            .anyMatch(blockingTask -> blockingTask.getCurrentStatus() != TaskStatus.APPROVED);

        if (hasIncompleteDependencies) {
            throw new IllegalStateException("Cannot submit task. One or more dependencies are not completed.");
        }

        if (task.getCurrentStatus() != TaskStatus.ASSIGNED && task.getCurrentStatus() != TaskStatus.REJECTED) {
            throw new IllegalStateException("Only ASSIGNED or REJECTED tasks can be submitted");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.SUBMITTED);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "SUBMITTED", "SUBMITTED", user, null);
        
        // Notify creator
        notificationService.createAndSend(task.getCreatedBy(), user, com.example.taskflow.notification.NotificationEvent.TASK_SUBMITTED,
            "Task Submitted", "Task " + task.getTitle() + " has been submitted for review.", updated, "task-submitted:" + task.getId());
            
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

        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be approved");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.APPROVED);
        task.setReviewedBy(reviewer); // Store who approved it
        
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "APPROVED", "APPROVED", reviewer, null);
        
        notificationService.createAndSend(task.getAssignedTo(), reviewer, com.example.taskflow.notification.NotificationEvent.TASK_APPROVED,
            "Task Approved", "Your task " + task.getTitle() + " was approved.", updated, "task-approved:" + task.getId());
            
        List<TaskDependency> dependents = taskDependencyRepository.findByBlocksTask_Id(taskId);
        for (TaskDependency dep : dependents) {
            Task blockedTask = dep.getTask();
            if (blockedTask.getAssignedTo() == null) continue;
            if (blockedTask.getAssignedTo().getId().equals(reviewer.getId())) continue;  // self-exclusion
            notificationService.createAndSend(
                blockedTask.getAssignedTo(),
                reviewer,
                com.example.taskflow.notification.NotificationEvent.DEPENDENCY_RESOLVED,
                "Dependency resolved: " + task.getTitle(),
                "A blocking task has been approved. You can now submit.",
                blockedTask,
                "dep-resolved:" + taskId + ":" + blockedTask.getId()
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

        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be rejected");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.REJECTED);
        task.setReviewedBy(reviewer);
        
        Task updated = taskRepository.save(task);

        // Note(frontend): migration - frontend needs to read rejection reason from /dashboard/activity/task/{taskId} instead of comments
        taskAuditService.recordStatus(updated, fromStatus.name(), "REJECTED", "REJECTED", reviewer, reason);
        
        notificationService.createAndSend(task.getAssignedTo(), reviewer, com.example.taskflow.notification.NotificationEvent.TASK_REJECTED,
            "Task Rejected", "Your task " + task.getTitle() + " was rejected. Reason: " + reason, updated, "task-rejected:" + task.getId());
            
        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        
        return dto;
    }

    // --- Helper Methods ---

    /** B-05: Scope filter for getTasksForUser */
    private boolean scopeFilter(TaskResponseDTO dto, User user, String scope) {
        if ("personal".equals(scope)) {
            return dto.isPersonal() && user.getUsername().equals(dto.getCreatedBy());
        }
        if ("org".equals(scope)) {
            return !dto.isPersonal();
        }
        return true;
    }

    private void validateReviewer(User reviewer, Task task) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(reviewer);
        if (!strategy.canReview(reviewer, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to review this task.");
        }
        // Org boundary check: reviewer must be in the same org as the task
        if (task.getOrganization() != null) {
            boolean inSameOrg = membershipRepository.existsByUserAndOrganization(reviewer, task.getOrganization());
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
        java.util.Map<Long, List<TaskDependency>> blockedByByTaskId = taskDependencyRepository.findAllByBlocksTaskIdIn(taskIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getBlocksTask().getId()));

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
        java.util.Map<Long, List<TaskDependency>> blockedByByTaskId = taskDependencyRepository.findAllByBlocksTaskIdIn(taskIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(d -> d.getBlocksTask().getId()));

        List<TaskResponseDTO> dtos = tasks.stream()
                .map(task -> mapToTaskResponseDTO(task, blockingByTaskId, blockedByByTaskId))
                .collect(java.util.stream.Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, total);
    }

    // --- DTO Mapping Methods ---
    private TaskResponseDTO mapToTaskResponseDTO(Task task) {
        return mapToTaskResponseDTO(task, null, null);
    }

    /** Batch-aware mapper — pass pre-fetched dependency maps to avoid N+1 */
    private TaskResponseDTO mapToTaskResponseDTO(Task task,
            java.util.Map<Long, List<TaskDependency>> blockingByTaskId,
            java.util.Map<Long, List<TaskDependency>> blockedByByTaskId) {
        Long taskId = task.getId();
        List<TaskDependency> blocking = (blockingByTaskId != null)
            ? blockingByTaskId.getOrDefault(taskId, java.util.Collections.emptyList())
            : taskDependencyRepository.findByTask_Id(taskId);
        List<TaskDependency> blockedBy = (blockedByByTaskId != null)
            ? blockedByByTaskId.getOrDefault(taskId, java.util.Collections.emptyList())
            : taskDependencyRepository.findByBlocksTask_Id(taskId);
        
        List<TaskSummaryDTO> blocksDto = blocking.stream()
            .map(d -> new TaskSummaryDTO(d.getBlocksTask().getId(), d.getBlocksTask().getTitle(), d.getBlocksTask().getCurrentStatus()))
            .collect(java.util.stream.Collectors.toList());
            
        List<TaskSummaryDTO> blockedByDto = blockedBy.stream()
            .map(d -> new TaskSummaryDTO(d.getTask().getId(), d.getTask().getTitle(), d.getTask().getCurrentStatus()))
            .collect(java.util.stream.Collectors.toList());

        return new TaskResponseDTO(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getAssignedTo() != null ? task.getAssignedTo().getUsername() : null,
            task.getCreatedBy() != null ? task.getCreatedBy().getUsername() : null,
            task.getReviewedBy() != null ? task.getReviewedBy().getUsername() : null,
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
            task.isPersonal(),
            task.isArchived(),
            task.getOrganization() != null ? task.getOrganization().getId() : null,
            task.getOrganization() != null ? task.getOrganization().getName() : null,
            task.getTeam() != null ? task.getTeam().getId() : null,
            task.getTeam() != null ? task.getTeam().getName() : null,
            task.getProject() != null ? task.getProject().getId() : null,
            task.getProject() != null ? task.getProject().getName() : null
        );
    }

    private TaskCommentDTO mapToTaskCommentDTO(TaskComment comment) {
        return new TaskCommentDTO(
            comment.getId(),
            comment.getUser().getUsername(),
            comment.getComment(),
            comment.getCreatedAt()
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
        Task task = getTask(taskId);

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setUser(user);
        comment.setComment(text);
        comment.setCreatedAt(LocalDateTime.now());
        TaskComment saved = taskCommentRepository.save(comment);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "COMMENTED", user, null);
        
        Set<User> participants = new HashSet<>();
        if (task.getAssignedTo() != null) participants.add(task.getAssignedTo());
        if (task.getCreatedBy() != null) participants.add(task.getCreatedBy());
        if (task.getReviewedBy() != null) participants.add(task.getReviewedBy());
        
        for (User p : participants) {
            notificationService.createAndSend(p, user, com.example.taskflow.notification.NotificationEvent.TASK_COMMENTED,
                "New Comment", user.getUsername() + " commented on task: " + task.getTitle(), task, "task-commented:" + task.getId() + ":" + user.getId());
        }
        
        return mapToTaskCommentDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<TaskCommentDTO> getComments(Long taskId, User user, Pageable pageable) {
        Task task = getTask(taskId);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId, pageable).map(this::mapToTaskCommentDTO);
    }

    @Transactional(readOnly = true)
    public List<com.example.taskflow.dto.AttachmentResponseDTO> getTaskAttachments(Long taskId) {
        return attachmentRepository.findByTask_Id(taskId).stream()
                .map(this::mapToAttachmentResponseDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    public com.example.taskflow.dto.AttachmentResponseDTO mapToAttachmentResponseDTO(Attachment attachment) {
        return new com.example.taskflow.dto.AttachmentResponseDTO(
            attachment.getId(),
            attachment.getOriginalFilename(),
            attachment.getContentType(),
            attachment.getFileSize(),
            attachment.getCreatedAt(),
            attachment.getUploadedBy() != null ? attachment.getUploadedBy().getUsername() : null
        );
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
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "CHECKLIST_ADDED", user, text);
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
        boolean isAssignee = item.getTask().getAssignedTo() != null && item.getTask().getAssignedTo().getId().equals(user.getId());
        if (!isCreator && !isAssignee) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("Only the creator or assignee of the checklist item can modify it.");
        }

        item.setIsCompleted(!item.getIsCompleted());
        ChecklistItem saved = checklistItemRepository.save(item);
        taskAuditService.recordStatus(item.getTask(), item.getTask().getCurrentStatus().name(), item.getTask().getCurrentStatus().name(), "CHECKLIST_TOGGLED", user, item.getText() + " completed: " + item.getIsCompleted());
        
        boolean allComplete = checklistItemRepository.findByTaskIdOrderByDisplayOrderAsc(taskId).stream().allMatch(ChecklistItem::getIsCompleted);
        if (allComplete) {
            notificationService.createAndSend(item.getTask().getCreatedBy(), user, com.example.taskflow.notification.NotificationEvent.CHECKLIST_UPDATED,
                "Checklist Completed", "All items completed on task: " + item.getTask().getTitle(), item.getTask(), "checklist-updated:" + item.getTask().getId());
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
        taskAuditService.recordStatus(item.getTask(), item.getTask().getCurrentStatus().name(), item.getTask().getCurrentStatus().name(), "CHECKLIST_REMOVED", user, item.getText());
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
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "CHECKLIST_REORDERED", user, null);
    }

    // --- Dependencies ---
    @Transactional
    public void addDependency(Long taskId, Long blocksTaskId, User user) {
        Task task = getTask(taskId);
        Task blocksTask = getTask(blocksTaskId);

        if (task.getId().equals(blocksTask.getId())) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }

        if (taskDependencyRepository.existsByTask_IdAndBlocksTask_Id(taskId, blocksTaskId)) {
            throw new IllegalArgumentException("Dependency already exists");
        }

        // Cycle Detection (DFS)
        if (detectCycle(blocksTask, task, new HashSet<>())) {
            throw new IllegalStateException("Cannot add dependency: It would create a circular dependency loop.");
        }

        TaskDependency dependency = new TaskDependency();
        dependency.setTask(task);
        dependency.setBlocksTask(blocksTask);
        try {
            taskDependencyRepository.saveAndFlush(dependency);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Dependency already exists");
        }
        
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "DEPENDENCY_ADDED", user, "Blocked by task " + blocksTaskId);
        
        if (task.getAssignedTo() != null && !task.getAssignedTo().getId().equals(user.getId())) {
            notificationService.createAndSend(
                task.getAssignedTo(),
                user,
                com.example.taskflow.notification.NotificationEvent.TASK_BLOCKED,
                "Task Blocked",
                "Your task is now blocked by: " + blocksTask.getTitle(),
                task,
                "task-blocked:" + taskId + ":" + blocksTaskId
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
                .map(TaskDependency::getBlocksTask)
                .anyMatch(nextTask -> detectCycle(nextTask, target, visited));
    }

    @Transactional
    public void removeDependency(Long taskId, Long depId, User user) {
        Task task = getTask(taskId);
        
        TaskDependency dependency = taskDependencyRepository.findById(depId)
                .orElseThrow(() -> new IllegalArgumentException("Dependency not found"));
                
        if (!dependency.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Dependency does not belong to this task");
        }
        
        taskDependencyRepository.delete(dependency);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "DEPENDENCY_REMOVED", user, "Dependency removed: " + depId);
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
            if (request.getDueDate().isBefore(LocalDateTime.now(zoneId).minusMinutes(5))) {
                throw new IllegalArgumentException("Due date cannot be in the past");
            }
            task.setDueDate(request.getDueDate());
        }
        if (request.getTags() != null) task.setTags(request.getTags());

        // B-15: Allow status updates for personal tasks only
        if (request.getStatus() != null) {
            if (task.isPersonal()) {
                task.setCurrentStatus(request.getStatus());
            } else {
                throw new IllegalArgumentException("Status can only be updated for personal tasks");
            }
        }
        
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(), "UPDATED", user, "Task details updated");
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

        // Clean up on-disk attachment files
        List<Attachment> attachments = attachmentRepository.findByTask_Id(taskId);
        for (Attachment att : attachments) {
            try {
                fileStorageService.delete(att.getId());
            } catch (Exception e) {
                // Log but continue
            }
        }

        // Delete related rows
        taskCommentRepository.deleteByTaskId(taskId);
        taskDependencyRepository.deleteByTaskId(taskId);
        taskDependencyRepository.deleteByBlocksTaskId(taskId);
        checklistItemRepository.deleteByTaskId(taskId);
        attachmentRepository.deleteByTaskId(taskId);

        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponseDTO toggleArchive(Long taskId, User user) {
        Task task = getTask(taskId);
        
        if (!roleStrategyFactory.getStrategy(user).canEdit(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to archive this task.");
        }

        task.setArchived(!task.isArchived());
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(),
            updated.isArchived() ? "ARCHIVED" : "UNARCHIVED", user, null);
        return mapToTaskResponseDTO(updated);
    }

    @Transactional
    public TaskResponseDTO reassignTask(Long taskId, User newAssignee, User user) {
        Task task = getTask(taskId);
        
        if (!roleStrategyFactory.getStrategy(user).canReassign(user, task)) {
            throw new com.example.taskflow.exception.UnauthorizedActionException("You are not authorized to reassign this task.");
        }
        
        User oldAssignee = task.getAssignedTo();
        task.setAssignedTo(newAssignee);
        task.setCurrentStatus(task.isPersonal() ? TaskStatus.TODO : TaskStatus.ASSIGNED); // RESET
        task.setReviewedBy(null); // RESET
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(), "REASSIGNED", user, "Reassigned to " + newAssignee.getUsername());
        
        if (oldAssignee != null) {
            notificationService.createAndSend(oldAssignee, user, com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
                "Task Reassigned", "Task " + task.getTitle() + " has been reassigned to " + newAssignee.getUsername(), updated, "task-unassigned:" + task.getId() + ":" + oldAssignee.getId());
        }
        
        notificationService.createAndSend(newAssignee, user, com.example.taskflow.notification.NotificationEvent.TASK_ASSIGNED,
            "You have a new task: " + task.getTitle(), "Task has been assigned to you.", updated, "task-assigned:" + task.getId() + ":" + newAssignee.getId());
            
        TaskResponseDTO dto = mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        
        return dto;
    }
}