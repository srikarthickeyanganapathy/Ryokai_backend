package com.example.taskflow.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.taskflow.domain.Attachment;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.exception.TaskNotFoundException;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.security.RoleStrategyFactory;
import com.example.taskflow.service.FileStorageService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/tasks/{taskId}/attachments", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class AttachmentController {

    private final FileStorageService fileStorageService;
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final RoleStrategyFactory roleStrategyFactory;
    private final com.example.taskflow.service.TaskWorkflowService taskWorkflowService;

    public AttachmentController(FileStorageService fileStorageService, 
                                TaskRepository taskRepository, UserService userService, RoleStrategyFactory roleStrategyFactory,
                                com.example.taskflow.service.TaskWorkflowService taskWorkflowService) {
        this.fileStorageService = fileStorageService;
        this.taskRepository = taskRepository;
        this.userService = userService;
        this.roleStrategyFactory = roleStrategyFactory;
        this.taskWorkflowService = taskWorkflowService;
    }

    @PostMapping
    @PreAuthorize("@customPermissionEvaluator.hasPermission(authentication, #taskId, 'Task', 'EDIT')")
    public ResponseEntity<?> uploadAttachment(@PathVariable Long taskId, @RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(10 * 1024 * 1024);
        }
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found"));
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        Attachment attachment = fileStorageService.store(file, task, user);
        com.example.taskflow.dto.AttachmentResponseDTO dto = taskWorkflowService.mapToAttachmentResponseDTO(attachment);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    @PreAuthorize("@customPermissionEvaluator.hasPermission(authentication, #taskId, 'Task', 'READ')")
    public ResponseEntity<List<com.example.taskflow.dto.AttachmentResponseDTO>> getAttachments(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskWorkflowService.getTaskAttachments(taskId));
    }

    @GetMapping("/{attachmentId}")
    @PreAuthorize("@customPermissionEvaluator.hasPermission(authentication, #taskId, 'Task', 'VIEW')")
    public ResponseEntity<Resource> download(@PathVariable Long taskId, @PathVariable Long attachmentId, 
                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        Attachment attachment = fileStorageService.getById(attachmentId);
        
        if (!attachment.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Attachment does not belong to this task");
        }
        
        Task task = attachment.getTask();
        if (!roleStrategyFactory.getStrategy(user).canViewTask(user, task)) {
            throw new UnauthorizedActionException("You don't have access to this file");
        }
        
        Resource resource = fileStorageService.load(attachment);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(attachment.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + sanitizeHeader(attachment.getOriginalFilename()) + "\"")
            .body(resource);
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("@customPermissionEvaluator.hasPermission(authentication, #taskId, 'Task', 'EDIT')")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long taskId, @PathVariable Long attachmentId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        Attachment attachment = fileStorageService.getById(attachmentId);
        
        if (!attachment.getTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Attachment does not belong to this task");
        }

        User user = userService.getCurrentUser(userDetails.getUsername());
        // Only task editors or the uploader themselves can delete
        if (!attachment.getUploadedBy().getId().equals(user.getId()) && !roleStrategyFactory.getStrategy(user).canEdit(user, attachment.getTask())) {
            throw new UnauthorizedActionException("You don't have permission to delete this file");
        }

        fileStorageService.delete(attachmentId);
        return ResponseEntity.noContent().build();
    }

    private String sanitizeHeader(String original) {
        return original.replace("\"", "\\\"");
    }
}
