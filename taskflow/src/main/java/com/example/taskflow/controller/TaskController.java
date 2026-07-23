package com.example.taskflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.dto.TaskUpdateRequestDTO;
import com.example.taskflow.service.TaskQueryService;
import com.example.taskflow.service.TaskLifecycleService;
import com.example.taskflow.service.TaskAuditService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskLifecycleService taskLifecycleService;
    private final UserService userService;
    private final TaskAuditService taskAuditService;

    public TaskController(
            TaskQueryService taskQueryService,
            TaskLifecycleService taskLifecycleService,
            UserService userService,
            TaskAuditService taskAuditService) {
        this.taskQueryService = taskQueryService;
        this.taskLifecycleService = taskLifecycleService;
        this.userService = userService;
        this.taskAuditService = taskAuditService;
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
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        return ResponseEntity.ok(taskQueryService.getTasksForUser(getCurrentUser(userDetails), safePage, scope, projectId, crewId));
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<TaskResponseDTO> getTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskQueryService.getTaskForUser(taskId, getCurrentUser(userDetails)));
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

    @PutMapping("/{taskId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> updateTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskUpdateRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskLifecycleService.updateTask(taskId, request, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DELETE')")
    public ResponseEntity<Void> deleteTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskLifecycleService.deleteTask(taskId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{taskId}/archive")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'ARCHIVE')")
    public ResponseEntity<TaskResponseDTO> toggleArchive(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskLifecycleService.toggleArchive(taskId, getCurrentUser(userDetails)));
    }
}
