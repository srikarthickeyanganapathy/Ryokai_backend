package com.example.taskflow.service;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.dto.TaskUpdateRequestDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.mapper.TaskResponseMapper;
import com.example.taskflow.repository.ChecklistItemRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.TaskCommentRepository;
import com.example.taskflow.repository.TaskDependencyRepository;
import com.example.taskflow.repository.TaskEvidenceRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.TaskStatusHistoryRepository;
import com.example.taskflow.strategy.task.TaskStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TaskLifecycleService {

    private final TaskRepository taskRepository;
    private final TaskStatusHistoryRepository taskStatusHistoryRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final TaskEvidenceRepository taskEvidenceRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final TaskStrategyFactory taskStrategyFactory;
    private final TaskAuditService taskAuditService;
    private final TaskResponseMapper taskResponseMapper;

    @Value("${app.timezone:UTC}")
    private ZoneId zoneId = ZoneId.of("UTC");

    @Transactional
    public TaskResponseDTO updateTask(Long taskId, TaskUpdateRequestDTO request, User user) {
        Task task = getTask(taskId);
        
        if (!taskStrategyFactory.get(task).canEdit(user, task)) {
            throw new UnauthorizedActionException("You are not authorized to edit this task.");
        }

        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getDueDate() != null) {
            if (request.getDueDate().isBefore(LocalDate.now(zoneId))) {
                throw new IllegalArgumentException("Due date cannot be in the past");
            }
            task.setDueDate(request.getDueDate());
        }
        if (request.getTags() != null) task.setTags(request.getTags());
        
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(), "UPDATED", user, "Task details updated", java.util.Map.of("priority", request.getPriority() != null ? request.getPriority().name() : "none"));
        return taskResponseMapper.mapToTaskResponseDTO(updated);
    }

    @Transactional
    public void deleteTask(Long taskId, User user) {
        Task task = getTask(taskId);

        if (!taskStrategyFactory.get(task).canDelete(user, task)) {
            throw new UnauthorizedActionException("You are not authorized to delete this task.");
        }
        
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), "DELETED", "DELETED", user, "Task deleted");

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
        
        if (!taskStrategyFactory.get(task).canArchive(user, task)) {
            throw new UnauthorizedActionException("You are not authorized to archive this task.");
        }

        task.setArchived(!task.isArchived());
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(),
            updated.isArchived() ? "ARCHIVED" : "UNARCHIVED", user, null, java.util.Map.of("archived", updated.isArchived()));
        return taskResponseMapper.mapToTaskResponseDTO(updated);
    }

    @Transactional
    public TaskResponseDTO reassignTask(Long taskId, User newAssignee, User user) {
        Task task = getTask(taskId);
        
        if (!taskStrategyFactory.get(task).canReassign(user, task)) {
            throw new UnauthorizedActionException("You are not authorized to reassign this task.");
        }

        if (task.getCurrentStatus() == TaskStatus.APPROVED || task.getCurrentStatus() == TaskStatus.COMPLETED) {
            throw new IllegalStateException("Completed or approved tasks cannot be reassigned.");
        }
        
        if (task.getOrg() != null) {
            OrganizationMembership reassignerMembership = membershipRepository.findByUserAndOrganization(user, task.getOrg())
                    .orElseThrow(() -> new UnauthorizedActionException("You are not a member of the task's organization."));
                    
            OrganizationMembership assigneeMembership = membershipRepository.findByUserAndOrganization(newAssignee, task.getOrg())
                    .orElseThrow(() -> new IllegalArgumentException("Assignee must be a member of the task's organization."));

            if (reassignerMembership.getOrgRole() != null && assigneeMembership.getOrgRole() != null) {
                Integer reassignerPriority = reassignerMembership.getOrgRole().getPriority();
                Integer assigneePriority = assigneeMembership.getOrgRole().getPriority();
                
                int rPriority = reassignerPriority != null ? reassignerPriority : 100;
                int aPriority = assigneePriority != null ? assigneePriority : 100;
                
                if (rPriority > aPriority && !user.isSuperAdmin()) {
                    throw new UnauthorizedActionException("Cannot assign to someone with higher role priority.");
                }
            }
        }
        
        task.setAssignee(newAssignee);
        if (task.isLocked() || task.getCurrentStatus() == TaskStatus.REJECTED) {
            task.setLocked(false);
            task.setCurrentStatus(TaskStatus.IN_PROGRESS);
        }
        Task updated = taskRepository.save(task);
        taskAuditService.recordStatus(updated, updated.getCurrentStatus().name(), updated.getCurrentStatus().name(), "REASSIGNED", user, "Reassigned to " + newAssignee.getUsername());
        return taskResponseMapper.mapToTaskResponseDTO(updated);
    }

    private Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));
    }
}
