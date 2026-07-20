package com.example.taskflow.controller;

import java.util.List;

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

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ProjectRequestDTO;
import com.example.taskflow.dto.ProjectResponseDTO;
import com.example.taskflow.service.ProjectService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/projects", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;

    public ProjectController(ProjectService projectService, UserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponseDTO>> getAllProjects(
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(projectService.getAllProjects(currentUser));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("hasPermission(#projectId, 'Project', 'READ')")
    public ResponseEntity<ProjectResponseDTO> getProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(projectService.getProject(projectId));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'Project', 'CREATE')")
    public ResponseEntity<ProjectResponseDTO> createProject(
            @Valid @RequestBody ProjectRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.getCurrentUser(userDetails.getUsername());
        ProjectResponseDTO project = projectService.createProject(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasPermission(#projectId, 'Project', 'EDIT')")
    public ResponseEntity<ProjectResponseDTO> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(projectService.updateProject(projectId, request, currentUser));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasPermission(#projectId, 'Project', 'DELETE')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/share/crew")
    @PreAuthorize("hasPermission(#projectId, 'Project', 'EDIT')")
    public ResponseEntity<ProjectResponseDTO> shareProjectToCrew(
            @PathVariable Long projectId,
            @RequestBody ProjectRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = userService.getCurrentUser(userDetails.getUsername());
        if (request.getCrewId() == null) {
            throw new IllegalArgumentException("crewId is required to share a project to a crew.");
        }
        return ResponseEntity.ok(projectService.shareProjectToCrew(projectId, request.getCrewId(), request.getCollaboratorIds(), currentUser));
    }
}
