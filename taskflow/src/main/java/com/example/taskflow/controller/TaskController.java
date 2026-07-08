package com.example.taskflow.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ChecklistItemDTO;
import com.example.taskflow.dto.ChecklistItemRequestDTO;
import com.example.taskflow.dto.CommentRequestDTO;
import com.example.taskflow.dto.RejectReasonDTO;
import com.example.taskflow.dto.TaskCommentDTO;
import com.example.taskflow.dto.TaskDependencyRequestDTO;
import com.example.taskflow.dto.TaskRequestDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.dto.TaskUpdateRequestDTO;
import com.example.taskflow.dto.TaskReassignRequestDTO;
import com.example.taskflow.dto.BulkAssignRequestDTO;
import com.example.taskflow.service.TaskAssignmentService;
import com.example.taskflow.service.TaskAuditService;
import com.example.taskflow.service.TaskWorkflowService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskController {

    private final TaskAssignmentService taskAssignmentService;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskAuditService taskAuditService;
    private final UserService userService;

    public TaskController(TaskAssignmentService taskAssignmentService,
            TaskWorkflowService taskWorkflowService,
            TaskAuditService taskAuditService,
            UserService userService) {
        this.taskAssignmentService = taskAssignmentService;
        this.taskWorkflowService = taskWorkflowService;
        this.taskAuditService = taskAuditService;
        this.userService = userService;
    }

    // Helper to get currently logged-in user securely
    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping
    public ResponseEntity<Page<TaskResponseDTO>> getTasks(Pageable pageable,
            @RequestParam(required = false) String scope,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        return ResponseEntity.ok(taskWorkflowService.getTasksForUser(getCurrentUser(userDetails), safePage, scope));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasPermission(null, 'Task', 'ASSIGN')")
    public ResponseEntity<TaskResponseDTO> assignTask(@Valid @RequestBody TaskRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        // The DTO contains the assignee username
        if (request.getAssigneeUsername() == null || request.getAssigneeUsername().isBlank()) {
            throw new IllegalArgumentException("Assignee username is required for non-personal tasks");
        }
        User assignee = userService.getCurrentUser(request.getAssigneeUsername());

        TaskResponseDTO response = taskAssignmentService.assignTask(
                request.getTitle(),
                request.getDescription(),
                assignee,
                creator,
                request.getPriority(),
                request.getDueDate(),
                request.getTags(),
                request.isPersonal(),
                request.getTeamId(),
                request.getProjectId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/personal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponseDTO> createPersonalTask(@Valid @RequestBody TaskRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        // For personal tasks, the assignee defaults to the creator
        User assignee = request.getAssigneeUsername() != null
                ? userService.getCurrentUser(request.getAssigneeUsername())
                : creator;

        TaskResponseDTO response = taskAssignmentService.assignTask(
                request.getTitle(),
                request.getDescription(),
                assignee,
                creator,
                request.getPriority(),
                request.getDueDate(),
                request.getTags(),
                true, // force isPersonal
                null, // no teamId for personal tasks
                request.getProjectId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize("hasPermission(null, 'Task', 'ASSIGN')")
    public ResponseEntity<List<TaskResponseDTO>> bulkAssignTasks(@Valid @RequestBody BulkAssignRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);

        String tagsJoined = request.getTags() != null ? String.join(",", request.getTags()) : null;

        List<TaskResponseDTO> response = taskAssignmentService.bulkAssignTasks(
                request.getTemplateId(),
                request.getTitle(),
                request.getDescription(),
                request.getAssigneeUsernames(),
                creator,
                request.getDueDate(),
                tagsJoined);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PostMapping("/{taskId}/submit")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> submitTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.submitTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")
    public ResponseEntity<TaskResponseDTO> approveTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.approveTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")
    public ResponseEntity<TaskResponseDTO> rejectTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody(required = false) RejectReasonDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(taskWorkflowService.rejectTask(taskId, getCurrentUser(userDetails), reason));
    }

    @GetMapping("/{taskId}/comments")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<Page<TaskCommentDTO>> getComments(@PathVariable @Min(1) Long taskId, Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        return ResponseEntity.ok(taskWorkflowService.getComments(taskId, getCurrentUser(userDetails), safePage));
    }

    @PostMapping("/{taskId}/comments")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'COMMENT')")
    public ResponseEntity<TaskCommentDTO> addComment(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody CommentRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        TaskCommentDTO response = taskWorkflowService.addComment(taskId, getCurrentUser(userDetails),
                request.getText());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasPermission(#id, 'Task', 'VIEW')")
    public ResponseEntity<Page<com.example.taskflow.dto.ActivityEventDTO>> getTaskHistory(
            @PathVariable @Min(1) Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(taskAuditService.getActivityFeedForTask(id, pageable));
    }

    @PostMapping("/{taskId}/checklists")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'CHECKLIST_EDIT')")
    public ResponseEntity<ChecklistItemDTO> addChecklistItem(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody ChecklistItemRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        ChecklistItemDTO response = taskWorkflowService.addChecklistItem(taskId, request.getText(),
                getCurrentUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{taskId}/checklists/{itemId}/toggle")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'CHECKLIST_EDIT')")
    public ResponseEntity<ChecklistItemDTO> toggleChecklistItem(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long itemId, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.toggleChecklistItem(taskId, itemId, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{taskId}/checklists/{itemId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'CHECKLIST_EDIT')")
    public ResponseEntity<Void> deleteChecklistItem(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long itemId, @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.deleteChecklistItem(taskId, itemId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{taskId}/checklists/order")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'CHECKLIST_EDIT')")
    public ResponseEntity<Void> reorderChecklistItems(@PathVariable @Min(1) Long taskId,
            @RequestBody java.util.List<Long> itemIds, @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.reorderChecklistItems(taskId, itemIds, getCurrentUser(userDetails));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskId}/dependencies")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DEPENDENCY_EDIT')")
    public ResponseEntity<Void> addDependency(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskDependencyRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.addDependency(taskId, request.getBlocksTaskId(), getCurrentUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> updateTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskUpdateRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.updateTask(taskId, request, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DELETE')")
    public ResponseEntity<Void> deleteTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.deleteTask(taskId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{taskId}/dependencies/{depId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DEPENDENCY_EDIT')")
    public ResponseEntity<Void> removeDependency(@PathVariable @Min(1) Long taskId, @PathVariable @Min(1) Long depId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.removeDependency(taskId, depId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{taskId}/reassign")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REASSIGN')")
    public ResponseEntity<TaskResponseDTO> reassignTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskReassignRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        User newAssignee = userService.getUserById(request.getAssigneeId());
        return ResponseEntity.ok(taskWorkflowService.reassignTask(taskId, newAssignee, getCurrentUser(userDetails)));
    }

    @PutMapping("/{taskId}/archive")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> toggleArchive(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.toggleArchive(taskId, getCurrentUser(userDetails)));
    }
}