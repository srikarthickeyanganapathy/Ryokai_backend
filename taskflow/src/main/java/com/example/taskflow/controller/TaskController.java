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
import com.example.taskflow.dto.TaskEvidenceDTO;
import com.example.taskflow.dto.TaskEvidenceRequestDTO;
import com.example.taskflow.service.TaskAssignmentService;
import com.example.taskflow.service.TaskAuditService;
import com.example.taskflow.service.TaskEvidenceService;
import com.example.taskflow.service.TaskWorkflowService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskController {

    private final TaskAssignmentService taskAssignmentService;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskAuditService taskAuditService;
    private final TaskEvidenceService taskEvidenceService;
    private final UserService userService;

    public TaskController(TaskAssignmentService taskAssignmentService,
            TaskWorkflowService taskWorkflowService,
            TaskAuditService taskAuditService,
            TaskEvidenceService taskEvidenceService,
            UserService userService) {
        this.taskAssignmentService = taskAssignmentService;
        this.taskWorkflowService = taskWorkflowService;
        this.taskAuditService = taskAuditService;
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

    @GetMapping
    public ResponseEntity<Page<TaskResponseDTO>> getTasks(Pageable pageable,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        return ResponseEntity.ok(taskWorkflowService.getTasksForUser(getCurrentUser(userDetails), safePage, scope, projectId, crewId));
    }

    // P2: GET single task by id (permission-gated VIEW)
    @GetMapping("/{taskId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<TaskResponseDTO> getTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.getTaskForUser(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasPermission(null, 'Task', 'ASSIGN')")
    public ResponseEntity<TaskResponseDTO> assignTask(@Valid @RequestBody TaskRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);

        // Option B: /assign is org-only. Crew tasks use POST /api/crews/{crewId}/tasks.
        if (request.getCrewId() != null) {
            throw new IllegalArgumentException("Crew tasks cannot be created via /assign. Use POST /api/crews/{crewId}/tasks instead.");
        }

        if (request.getAssigneeUsername() == null || request.getAssigneeUsername().isBlank()) {
            throw new IllegalArgumentException("Assignee username is required for org tasks");
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
                request.getProjectId(),
                null); // crewId: always null - crew tasks go through CrewController
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/personal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TaskResponseDTO> createPersonalTask(@Valid @RequestBody TaskRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User creator = getCurrentUser(userDetails);
        if (request.getAssigneeUsername() != null
                && !request.getAssigneeUsername().equals(userDetails.getUsername())) {
            throw new IllegalArgumentException("Personal tasks can only be assigned to yourself");
        }
        User assignee = creator;

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

        // Validation: must provide either teamId or assigneeUsernames
        boolean hasTeam = request.getTeamId() != null;
        boolean hasUsernames = request.getAssigneeUsernames() != null && !request.getAssigneeUsernames().isEmpty();
        if (!hasTeam && !hasUsernames) {
            throw new IllegalArgumentException("Either teamId or assigneeUsernames must be provided");
        }

        String tagsJoined = request.getTags() != null ? String.join(",", request.getTags()) : null;

        List<TaskResponseDTO> response = taskAssignmentService.bulkAssignTasks(
                request.getTitle(),
                request.getDescription(),
                request.getAssigneeUsernames(),
                creator,
                request.getDueDate(),
                tagsJoined,
                request.getTeamId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PostMapping("/{taskId}/submit")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> submitTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.submitTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/complete")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> completePersonalTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.completePersonalTask(taskId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{taskId}/approve")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")
    public ResponseEntity<TaskResponseDTO> approveTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.approveTask(taskId, getCurrentUser(userDetails)));
    }

    // SM-M01 fix: spec requires rejection_reason NOT NULL enforced at DTO + DB level.
    // - DTO now has @NotBlank on `reason`.
    // - Body is now required (was required=false).
    // - V39 migration adds partial CHECK constraint at DB level.
    @PostMapping("/{taskId}/reject")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'REVIEW')")
    public ResponseEntity<TaskResponseDTO> rejectTask(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody RejectReasonDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.rejectTask(taskId, getCurrentUser(userDetails), request.getReason()));
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
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<TaskCommentDTO> addComment(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody CommentRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        TaskCommentDTO response = taskWorkflowService.addComment(taskId, getCurrentUser(userDetails),
                request.getText(), request.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // P1: Task evidence
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
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<ChecklistItemDTO> addChecklistItem(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody ChecklistItemRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        ChecklistItemDTO response = taskWorkflowService.addChecklistItem(taskId, request.getText(),
                getCurrentUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{taskId}/checklists/{itemId}/toggle")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<ChecklistItemDTO> toggleChecklistItem(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long itemId, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.toggleChecklistItem(taskId, itemId, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{taskId}/checklists/{itemId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<Void> deleteChecklistItem(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long itemId, @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.deleteChecklistItem(taskId, itemId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{taskId}/checklists/order")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<Void> reorderChecklistItems(@PathVariable @Min(1) Long taskId,
            @RequestBody java.util.List<Long> itemIds, @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.reorderChecklistItems(taskId, itemIds, getCurrentUser(userDetails));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskId}/dependencies")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'DEPENDENCY_EDIT')")
    public ResponseEntity<Void> addDependency(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody TaskDependencyRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        taskWorkflowService.addDependency(taskId, request.getDependsOnId(), getCurrentUser(userDetails));
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

    // RB-M04 fix: switched from 'EDIT' to 'ARCHIVE' permission.
    // CustomPermissionEvaluator now routes ARCHIVE to canArchive (was canDelete).
    @PutMapping("/{taskId}/archive")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'ARCHIVE')")
    public ResponseEntity<TaskResponseDTO> toggleArchive(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.toggleArchive(taskId, getCurrentUser(userDetails)));
    }

    // SM-M03 fix: spec state machine says "SUBMITTED --> ASSIGNED : assignee recalls".
    // Previously no endpoint existed - once an assignee submitted, they had to
    // wait for the reviewer to approve or reject.
    @PostMapping("/{taskId}/recall")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> recallTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.recallTask(taskId, getCurrentUser(userDetails)));
    }

    // Spec state machine for crew tasks: TODO (unclaimed) → ASSIGNED (claimed) → COMPLETED
    // Any crew member can complete a crew task via this endpoint.
    // Also supports TODO → COMPLETED (implicit claim + complete in one step).
    @PostMapping("/{taskId}/complete-crew")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> completeCrewTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.completeCrewTask(taskId, getCurrentUser(userDetails)));
    }

    // Spec: crew tasks use a claiming model — anyone creates, anyone claims (first-taker wins).
    // Transitions an unclaimed crew task from TODO → ASSIGNED with the claimer as assignee.
    @PostMapping("/{taskId}/claim")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<TaskResponseDTO> claimTask(@PathVariable @Min(1) Long taskId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(taskWorkflowService.claimTask(taskId, getCurrentUser(userDetails)));
    }
}