package com.example.taskflow.mapper;

import com.example.taskflow.domain.ChecklistItem;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskComment;
import com.example.taskflow.domain.TaskDependency;
import com.example.taskflow.dto.ChecklistItemDTO;
import com.example.taskflow.dto.TaskCommentDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.dto.TaskSummaryDTO;
import com.example.taskflow.repository.TaskDependencyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class TaskResponseMapper {

    private final TaskDependencyRepository taskDependencyRepository;

    public TaskResponseMapper(TaskDependencyRepository taskDependencyRepository) {
        this.taskDependencyRepository = taskDependencyRepository;
    }

    public TaskResponseDTO mapToTaskResponseDTO(Task task) {
        return mapToTaskResponseDTO(task, null, null);
    }

    /** Batch-aware mapper - pass pre-fetched dependency maps to avoid N+1 */
    public TaskResponseDTO mapToTaskResponseDTO(Task task,
            Map<Long, List<TaskDependency>> blockingByTaskId,
            Map<Long, List<TaskDependency>> blockedByByTaskId) {
        Long taskId = task.getId();
        List<TaskDependency> prerequisites = (blockingByTaskId != null)
            ? blockingByTaskId.getOrDefault(taskId, java.util.Collections.emptyList())
            : taskDependencyRepository.findByTask_Id(taskId);
        List<TaskDependency> downstream = (blockedByByTaskId != null)
            ? blockedByByTaskId.getOrDefault(taskId, java.util.Collections.emptyList())
            : taskDependencyRepository.findByDependsOn_Id(taskId);
        
        List<TaskSummaryDTO> blockedByDto = prerequisites.stream()
            .map(d -> new TaskSummaryDTO(d.getDependsOn().getId(), d.getDependsOn().getTitle(), d.getDependsOn().getCurrentStatus()))
            .collect(Collectors.toList());
            
        List<TaskSummaryDTO> blocksDto = downstream.stream()
            .map(d -> new TaskSummaryDTO(d.getTask().getId(), d.getTask().getTitle(), d.getTask().getCurrentStatus()))
            .collect(Collectors.toList());

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
            task.getChecklists() != null ? task.getChecklists().stream().map(this::mapToChecklistItemDTO).collect(Collectors.toList()) : java.util.Collections.emptyList(),
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

    public TaskCommentDTO mapToTaskCommentDTO(TaskComment comment) {
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

    public TaskCommentDTO mapToTaskCommentDTOWithReplies(TaskComment comment) {
        List<TaskCommentDTO> replies = comment.getReplies() != null
                ? comment.getReplies().stream().map(this::mapToTaskCommentDTO).collect(Collectors.toList())
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

    public ChecklistItemDTO mapToChecklistItemDTO(ChecklistItem item) {
        return new ChecklistItemDTO(
            item.getId(),
            item.getText(),
            item.getIsCompleted(),
            item.getDisplayOrder(),
            item.getCreatedBy() != null ? item.getCreatedBy().getUsername() : null
        );
    }
}
