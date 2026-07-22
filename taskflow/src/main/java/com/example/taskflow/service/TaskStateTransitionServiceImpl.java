package com.example.taskflow.service;

import com.example.taskflow.domain.CrewMember;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskDependency;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.mapper.TaskResponseMapper;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.repository.TaskDependencyRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.strategy.task.Approvable;
import com.example.taskflow.strategy.task.TaskLifecycleStrategy;
import com.example.taskflow.strategy.task.TaskStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Implementation of TaskStateTransitionService.
 * 
 * OCP (Open-Closed Principle) Note:
 * This class is designed to be open for extension but closed for modification.
 * For complex state transitions (like submit, approve, reject), it avoids hardcoded
 * conditional logic (e.g., if taskType == X) by delegating to the TaskLifecycleStrategy
 * via TaskStrategyFactory. If a new task type is introduced that requires an approval
 * workflow, the new strategy simply needs to implement the Approvable interface.
 * This class will automatically support the new workflow without any modifications.
 */
@Service
@RequiredArgsConstructor
public class TaskStateTransitionServiceImpl implements TaskStateTransitionService {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskAuditService taskAuditService;
    private final NotificationService notificationService;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final TaskStrategyFactory taskStrategyFactory;
    private final TaskResponseMapper taskResponseMapper;

    @Override
    @Transactional
    public TaskResponseDTO completePersonalTask(Long taskId, User user) {
        Task task = getTask(taskId);
        if (!task.isPersonal() || task.getCreator() == null || !task.getCreator().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("You can only complete your own personal tasks.");
        }
        if (task.getCurrentStatus() == TaskStatus.COMPLETED) {
            return taskResponseMapper.mapToTaskResponseDTO(task);
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.COMPLETED);
        Task updated = taskRepository.save(task);

        taskAuditService.recordStatus(updated, fromStatus.name(), "COMPLETED", "COMPLETED", user, "Personal task completed");
        
        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    @Override
    @Transactional
    public TaskResponseDTO submitTask(Long taskId, User user) {
        Task task = getTask(taskId);
        Approvable approvable = requireApprovable(task);

        if (!approvable.canSubmit(user, task)) {
            throw new UnauthorizedActionException("You are not authorized to submit this task.");
        }
        if (task.getCurrentStatus() != TaskStatus.IN_PROGRESS && task.getCurrentStatus() != TaskStatus.REJECTED && task.getCurrentStatus() != TaskStatus.TODO) {
            throw new IllegalStateException("Only TODO, IN_PROGRESS or REJECTED tasks can be submitted.");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.SUBMITTED);
        task.setRejectionReason(null);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "SUBMITTED", "SUBMITTED", user, null);
        
        notificationService.createAndSend(task.getCreator(), user, NotificationEvent.TASK_SUBMITTED,
            "Task Submitted", "Task " + task.getTitle() + " has been submitted for review.", updated, "task-submitted:" + task.getId(), user);
            
        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    @Override
    @Transactional
    public TaskResponseDTO approveTask(Long taskId, User reviewer) {
        Task task = getTask(taskId);
        Approvable approvable = requireApprovable(task);
        
        if (!approvable.canApprove(reviewer, task)) {
            throw new UnauthorizedActionException("You are not authorized to approve this task.");
        }
        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be approved");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.APPROVED);
        task.setReviewer(reviewer);
        task.setRejectionReason(null);
        Task updated = taskRepository.save(task);
        
        taskAuditService.recordStatus(updated, fromStatus.name(), "APPROVED", "APPROVED", reviewer, null);
        notificationService.createAndSend(task.getAssignee(), reviewer, NotificationEvent.TASK_APPROVED,
            "Task Approved", "Your task " + task.getTitle() + " was approved.", updated, "task-approved:" + task.getId(), reviewer);
            
        List<TaskDependency> dependents = taskDependencyRepository.findByDependsOn_Id(taskId);
        for (TaskDependency dep : dependents) {
            Task blockedTask = dep.getTask();
            if (blockedTask.getAssignee() != null && !blockedTask.getAssignee().getId().equals(reviewer.getId())) {
                notificationService.createAndSend(blockedTask.getAssignee(), reviewer, NotificationEvent.DEPENDENCY_RESOLVED,
                    "Dependency resolved: " + task.getTitle(), "A blocking task has been approved.", blockedTask, "dep-resolved:" + taskId + ":" + blockedTask.getId(), reviewer);
            }
        }

        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    @Override
    @Transactional
    public TaskResponseDTO rejectTask(Long taskId, User reviewer, String reason) {
        Task task = getTask(taskId);
        Approvable approvable = requireApprovable(task);
        
        if (!approvable.canReject(reviewer, task)) {
            throw new UnauthorizedActionException("You are not authorized to reject this task.");
        }
        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be rejected");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.REJECTED);
        task.setReviewer(reviewer);
        task.setRejectionReason(reason);
        User formerAssignee = task.getAssignee();
        task.setAssignee(null);
        
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "REJECTED", "REJECTED", reviewer, reason);
        
        if (formerAssignee != null) {
            notificationService.createAndSend(formerAssignee, reviewer, NotificationEvent.TASK_REJECTED,
                "Task Rejected", "Your task " + task.getTitle() + " was rejected. Reason: " + reason, updated, "task-rejected:" + task.getId(), reviewer);
        }
            
        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    @Override
    @Transactional
    public TaskResponseDTO recallTask(Long taskId, User user) {
        Task task = getTask(taskId);
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("Only the assignee can recall a submitted task.");
        }
        if (task.getCurrentStatus() != TaskStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED tasks can be recalled.");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setCurrentStatus(TaskStatus.IN_PROGRESS);
        Task updated = taskRepository.save(task);
        
        taskAuditService.recordStatus(updated, fromStatus.name(), "IN_PROGRESS", "RECALLED", user,
                "Assignee recalled submission", Map.of("recalledBy", user.getUsername()));

        if (task.getCreator() != null && !task.getCreator().getId().equals(user.getId())) {
            notificationService.createAndSend(task.getCreator(), user, NotificationEvent.TASK_SUBMITTED,
                "Task Recalled", "Task " + task.getTitle() + " was recalled.", updated, "task-recalled:" + task.getId(), user);
        }

        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    @Override
    @Transactional
    public TaskResponseDTO completeCrewTask(Long taskId, User user) {
        Task task = getTask(taskId);
        if (task.getCrew() == null) {
            throw new IllegalArgumentException("This endpoint is only for crew tasks.");
        }
        if (task.getCurrentStatus() == TaskStatus.COMPLETED) {
            return taskResponseMapper.mapToTaskResponseDTO(task);
        }
        if (task.getCurrentStatus() != TaskStatus.IN_PROGRESS && task.getCurrentStatus() != TaskStatus.TODO) {
            throw new IllegalStateException("Only TODO or ASSIGNED crew tasks can be completed.");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        if (fromStatus == TaskStatus.TODO && task.getAssignee() == null) {
            task.setAssignee(user);
        }

        task.setCurrentStatus(TaskStatus.COMPLETED);
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, fromStatus.name(), "COMPLETED", "COMPLETED", user, "Crew task marked complete");

        if (task.getCrew().getMembers() != null) {
            for (CrewMember cm : task.getCrew().getMembers()) {
                if (cm.getUser() != null && !cm.getUser().getId().equals(user.getId())) {
                    notificationService.createAndSend(cm.getUser(), user, NotificationEvent.TASK_APPROVED,
                        "Crew Task Completed", "Crew task " + task.getTitle() + " completed.", updated, "crew-task-completed:" + task.getId() + ":" + cm.getUser().getId(), user);
                }
            }
        }

        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    @Override
    @Transactional
    public TaskResponseDTO claimTask(Long taskId, User user) {
        Task task = getTask(taskId);
        if (task.getCrew() == null) {
            throw new IllegalArgumentException("Only crew tasks can be claimed.");
        }
        if (task.getCurrentStatus() != TaskStatus.TODO) {
            throw new IllegalStateException("Only unclaimed crew tasks can be claimed.");
        }
        if (task.getAssignee() != null) {
            throw new IllegalStateException("This task is already claimed");
        }
        if (task.getCrew().getMembers().stream().noneMatch(cm -> cm.getUser().getId().equals(user.getId()))) {
            throw new UnauthorizedActionException("You must be a member of this crew to claim a task.");
        }

        TaskStatus fromStatus = task.getCurrentStatus();
        task.setAssignee(user);
        task.setCurrentStatus(TaskStatus.IN_PROGRESS);
        
        Task updated;
        try {
            updated = taskRepository.save(task);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw new IllegalStateException("Task already claimed");
        }

        taskAuditService.recordStatus(updated, fromStatus.name(), "IN_PROGRESS", "CLAIMED", user, "Task claimed", Map.of("claimedBy", user.getUsername()));

        if (task.getCreator() != null && !task.getCreator().getId().equals(user.getId())) {
            notificationService.createAndSend(task.getCreator(), user, NotificationEvent.TASK_ASSIGNED,
                "Task Claimed", "Crew task " + task.getTitle() + " was claimed.", updated, "crew-task-claimed:" + task.getId(), user);
        }

        TaskResponseDTO dto = taskResponseMapper.mapToTaskResponseDTO(updated);
        realtimeBroadcaster.broadcastTaskUpdate(dto, updated.getId());
        return dto;
    }

    private Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));
    }

    private Approvable requireApprovable(Task task) {
        TaskLifecycleStrategy s = taskStrategyFactory.get(task);
        if (!(s instanceof Approvable)) {
            throw new UnauthorizedActionException("Task mode " + s.getSupportedMode() + " does not support approvals");
        }
        return (Approvable) s;
    }
}
