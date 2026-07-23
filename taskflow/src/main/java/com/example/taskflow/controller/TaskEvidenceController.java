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
import java.util.List;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskEvidenceDTO;
import com.example.taskflow.dto.TaskEvidenceRequestDTO;
import com.example.taskflow.service.TaskEvidenceService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskEvidenceController {

    private final TaskEvidenceService taskEvidenceService;
    private final UserService userService;

    public TaskEvidenceController(
            TaskEvidenceService taskEvidenceService,
            UserService userService) {
        this.taskEvidenceService = taskEvidenceService;
        this.userService = userService;
    }

    // Helper to get currently logged-in user securely
    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping("/{taskId}/evidence")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<List<TaskEvidenceDTO>> listEvidence(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskEvidenceService.listEvidence(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/evidence")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskEvidenceDTO> addEvidence(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskEvidenceRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskEvidenceService.addEvidence(taskId, request, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{taskId}/evidence/{evidenceId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<Void> deleteEvidence(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long evidenceId,
            @AuthenticationPrincipal UserDetails userDetails) {
        taskEvidenceService.deleteEvidence(taskId, evidenceId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}
