package com.example.taskflow.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.TaskTemplate;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskTemplateDTO;
import com.example.taskflow.dto.TaskTemplateRequestDTO;
import com.example.taskflow.repository.TaskTemplateRepository;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/task-templates", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class TaskTemplateController {

    private final TaskTemplateRepository templateRepository;
    private final UserService userService;

    public TaskTemplateController(TaskTemplateRepository templateRepository, UserService userService) {
        this.templateRepository = templateRepository;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping
    public ResponseEntity<List<TaskTemplateDTO>> getAllTemplates() {
        List<TaskTemplateDTO> templates = templateRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskTemplateDTO> getTemplate(@PathVariable Long id) {
        TaskTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        return ResponseEntity.ok(toDTO(template));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'Task', 'ASSIGN')")
    public ResponseEntity<TaskTemplateDTO> createTemplate(
            @Valid @RequestBody TaskTemplateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        TaskTemplate template = new TaskTemplate();
        template.setName(request.getName());
        template.setDefaultTitle(request.getDefaultTitle());
        template.setDefaultDescription(request.getDefaultDescription());
        template.setDefaultPriority(request.getDefaultPriority());
        template.setCreatedBy(user);
        template.setCreatedAt(java.time.LocalDateTime.now());
        TaskTemplate saved = templateRepository.save(template);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'Task', 'ASSIGN')")
    public ResponseEntity<TaskTemplateDTO> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody TaskTemplateRequestDTO request) {
        TaskTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        if (request.getName() != null) template.setName(request.getName());
        if (request.getDefaultTitle() != null) template.setDefaultTitle(request.getDefaultTitle());
        if (request.getDefaultDescription() != null) template.setDefaultDescription(request.getDefaultDescription());
        if (request.getDefaultPriority() != null) template.setDefaultPriority(request.getDefaultPriority());
        TaskTemplate saved = templateRepository.save(template);
        return ResponseEntity.ok(toDTO(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'Task', 'ASSIGN')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        if (!templateRepository.existsById(id)) {
            throw new IllegalArgumentException("Template not found: " + id);
        }
        templateRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private TaskTemplateDTO toDTO(TaskTemplate template) {
        return new TaskTemplateDTO(
            template.getId(),
            template.getName(),
            template.getDefaultTitle(),
            template.getDefaultDescription(),
            template.getDefaultPriority(),
            template.getCreatedBy() != null ? template.getCreatedBy().getUsername() : null,
            template.getCreatedAt()
        );
    }
}
