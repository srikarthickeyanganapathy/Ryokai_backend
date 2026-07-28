package com.example.taskflow.task.application.command;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskComment;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.response.TaskCommentDTO;
import com.example.taskflow.task.mapper.TaskResponseMapper;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.task.infrastructure.persistence.TaskCommentRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.taskflow.notification.application.NotificationService;

@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final NotificationService notificationService;
    private final TaskResponseMapper taskResponseMapper;

    @Transactional(readOnly = true)
    public Page<TaskCommentDTO> getComments(Long taskId, User user, Pageable pageable) {
        taskRepository.findById(taskId).orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return taskCommentRepository.findByTaskIdAndParentIsNullOrderByCreatedAtAsc(taskId, pageable)
                .map(taskResponseMapper::mapToTaskCommentDTOWithReplies);
    }

    @Transactional
    public TaskCommentDTO addComment(Long taskId, User user, String text) {
        return addComment(taskId, user, text, null);
    }

    @Transactional
    public TaskCommentDTO addComment(Long taskId, User user, String text, Long parentId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(user);
        comment.setComment(text);

        if (parentId != null) {
            TaskComment parent = taskCommentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found"));
            comment.setParent(parent);
        }

        TaskComment saved = taskCommentRepository.save(comment);

        // Notify assignee if someone else commented
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(user.getId())) {
            notificationService.createAndSend(
                    task.getAssignee(),
                    user, // exclude author
                    NotificationEvent.TASK_COMMENTED,
                    "New Comment",
                    user.getUsername() + " commented on: " + task.getTitle(),
                    task,
                    "comment-task-" + task.getId(),
                    user
            );
        }

        return taskResponseMapper.mapToTaskCommentDTO(saved);
    }
}