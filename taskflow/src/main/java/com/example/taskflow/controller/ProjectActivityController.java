package com.example.taskflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.ProjectActivityLog;
import com.example.taskflow.repository.ProjectActivityLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectActivityController {

    private final ProjectActivityLogRepository projectActivityLogRepository;

    @GetMapping("/{projectId}/activities")
    @PreAuthorize("hasPermission(#projectId, 'Project', 'VIEW')")
    public ResponseEntity<Page<ProjectActivityLog>> getProjectActivities(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(projectActivityLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(page, size)));
    }
}
