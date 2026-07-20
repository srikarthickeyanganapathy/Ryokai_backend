package com.example.taskflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskDependencyRequestDTO;
import com.example.taskflow.service.TaskDependencyService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskDependencyController {

    private final TaskDependencyService taskDependencyService;
    private final UserService userService;

    public TaskDependencyController(
            TaskDependencyService taskDependencyService,
            UserService userService) {
        this.taskDependencyService = taskDependencyService;
        this.userService = userService;
    }

    // Helper to get currently logged-in user securely
    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping("/{taskId}/dependencies")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DEPENDENCY_EDIT')")
    public ResponseEntity<Void> addDependency(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskDependencyRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        taskDependencyService.addDependency(taskId, request.getDependsOnId(), getCurrentUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{taskId}/dependencies/{depId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DEPENDENCY_EDIT')")
    public ResponseEntity<Void> removeDependency(@PathVariable @Min(1) Long taskId, @PathVariable @Min(1) Long depId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskDependencyService.removeDependency(taskId, depId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}
