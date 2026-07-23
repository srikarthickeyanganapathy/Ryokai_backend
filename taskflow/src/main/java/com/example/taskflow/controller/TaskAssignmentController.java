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
import com.example.taskflow.dto.TaskRequestDTO;
import com.example.taskflow.dto.TaskResponseDTO;
import com.example.taskflow.dto.TaskReassignRequestDTO;
import com.example.taskflow.dto.BulkAssignRequestDTO;
import com.example.taskflow.service.TaskAssignmentService;
import com.example.taskflow.service.TaskBulkAssignmentService;
import com.example.taskflow.service.TaskLifecycleService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskAssignmentController {

    private final TaskAssignmentService taskAssignmentService;
    private final TaskBulkAssignmentService taskBulkAssignmentService;
    private final TaskLifecycleService taskLifecycleService;
    private final UserService userService;

    public TaskAssignmentController(
            TaskAssignmentService taskAssignmentService,
            TaskBulkAssignmentService taskBulkAssignmentService,
            TaskLifecycleService taskLifecycleService,
            UserService userService) {
        this.taskAssignmentService = taskAssignmentService;
        this.taskBulkAssignmentService = taskBulkAssignmentService;
        this.taskLifecycleService = taskLifecycleService;
        this.userService = userService;
    }

    // Helper to get currently logged-in user securely
    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping("/assign")
    @PreAuthorize("hasPermission(#request, 'TASK_CREATE')")
    public ResponseEntity<TaskResponseDTO> assignTask(@Valid @RequestBody TaskRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);

        if (request.getAssigneeUsername() == null || request.getAssigneeUsername().isBlank()) {
            throw new IllegalArgumentException("Assignee username is required for org tasks");
        }
        User assignee = userService.getCurrentUser(request.getAssigneeUsername());

        com.example.taskflow.domain.TaskScope scope = com.example.taskflow.domain.TaskScope.org(request.getTeamId());
        if (request.getTeamId() != null) {
            scope = com.example.taskflow.domain.TaskScope.org(request.getTeamId());
        } else {
            scope = com.example.taskflow.domain.TaskScope.org(null);
        }
        
        com.example.taskflow.dto.TaskAssignmentCommand cmd = com.example.taskflow.dto.TaskAssignmentCommand.builder()
                .request(request)
                .assignor(creator)
                .assignee(assignee)
                .scope(scope)
                .build();
                
        TaskResponseDTO response = taskAssignmentService.assignTask(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/personal")
    @PreAuthorize("hasPermission(#request, 'TASK_CREATE')")
    public ResponseEntity<TaskResponseDTO> createPersonalTask(@Valid @RequestBody TaskRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        if (request.getAssigneeUsername() != null
                && !request.getAssigneeUsername().equals(userDetails.getUsername())) {
            throw new IllegalArgumentException("Personal tasks can only be assigned to yourself");
        }
        User assignee = creator;

        com.example.taskflow.dto.TaskAssignmentCommand cmd = com.example.taskflow.dto.TaskAssignmentCommand.builder()
                .request(request)
                .assignor(creator)
                .assignee(assignee)
                .scope(com.example.taskflow.domain.TaskScope.personal())
                .build();
                
        TaskResponseDTO response = taskAssignmentService.assignTask(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/crew")
    @PreAuthorize("hasPermission(#request, 'TASK_CREATE')")
    public ResponseEntity<TaskResponseDTO> createCrewTask(@Valid @RequestBody TaskRequestDTO request,
            @RequestParam Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        User assignee = null;
        if (request.getAssigneeUsername() != null && !request.getAssigneeUsername().isBlank()) {
            assignee = userService.getCurrentUser(request.getAssigneeUsername());
        }

        com.example.taskflow.dto.TaskAssignmentCommand cmd = com.example.taskflow.dto.TaskAssignmentCommand.builder()
                .request(request)
                .assignor(creator)
                .assignee(assignee)
                .scope(com.example.taskflow.domain.TaskScope.crew(crewId))
                .build();
                
        TaskResponseDTO response = taskAssignmentService.assignTask(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize("hasPermission(#request, 'TASK_CREATE')")
    public ResponseEntity<com.example.taskflow.dto.BulkAssignResponseDTO> bulkAssignTasks(
            @Valid @RequestBody com.example.taskflow.dto.BulkAssignRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        String tagsJoined = request.getTags() != null ? String.join(",", request.getTags()) : null;
        com.example.taskflow.dto.BulkAssignResponseDTO response = taskBulkAssignmentService.bulkAssignTasks(
                request.getTitle(),
                request.getDescription(),
                request.getAssigneeUsernames(),
                creator,
                request.getDueDate(),
                tagsJoined,
                request.getTeamId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{taskId}/reassign")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REASSIGN')")
    public ResponseEntity<TaskResponseDTO> reassignTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskReassignRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        User newAssignee = userService.getUserById(request.getAssigneeId());
        return ResponseEntity.ok(taskLifecycleService.reassignTask(taskId, newAssignee, getCurrentUser(userDetails)));
    }
}
