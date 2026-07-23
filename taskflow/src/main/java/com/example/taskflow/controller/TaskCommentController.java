package com.example.taskflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.CommentRequestDTO;
import com.example.taskflow.dto.TaskCommentDTO;
import com.example.taskflow.service.TaskCommentService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskCommentController {

    private final TaskCommentService taskCommentService;
    private final UserService userService;

    public TaskCommentController(TaskCommentService taskCommentService, UserService userService) {
        this.taskCommentService = taskCommentService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping("/{taskId}/comments")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<Page<TaskCommentDTO>> getComments(@PathVariable @Min(1) Long taskId, Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        Pageable safePage = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        return ResponseEntity.ok(taskCommentService.getComments(taskId, getCurrentUser(userDetails), safePage));
    }

    @PostMapping("/{taskId}/comments")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<TaskCommentDTO> addComment(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody CommentRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        TaskCommentDTO response = taskCommentService.addComment(taskId, getCurrentUser(userDetails),
                request.getText(), request.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
