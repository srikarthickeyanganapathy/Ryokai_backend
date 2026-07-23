package com.example.taskflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.RejectReasonDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.service.TaskStateTransitionService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskStateController {

    private final TaskStateTransitionService taskStateTransitionService;
    private final UserService userService;

    public TaskStateController(
            TaskStateTransitionService taskStateTransitionService,
            UserService userService) {
        this.taskStateTransitionService = taskStateTransitionService;
        this.userService = userService;
    }

    // Helper to get currently logged-in user securely
    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping("/{taskId}/submit")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> submitTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.submitTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> completePersonalTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.completePersonalTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")
    public ResponseEntity<TaskResponseDTO> approveTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.approveTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")
    public ResponseEntity<TaskResponseDTO> rejectTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody RejectReasonDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.rejectTask(taskId, getCurrentUser(userDetails), request.getReason()));
    }

    @PostMapping("/{taskId}/recall")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> recallTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.recallTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/complete-crew")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> completeCrewTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.completeCrewTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/claim")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> claimTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskStateTransitionService.claimTask(taskId, getCurrentUser(userDetails)));
    }
}
