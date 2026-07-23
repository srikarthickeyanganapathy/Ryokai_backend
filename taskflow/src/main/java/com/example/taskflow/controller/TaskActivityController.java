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

import com.example.taskflow.domain.TaskActivityLog;
import com.example.taskflow.repository.TaskActivityLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskActivityController {

    private final TaskActivityLogRepository activityLogRepository;

    @GetMapping("/{taskId}/activities")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<Page<TaskActivityLog>> getTaskActivities(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId, PageRequest.of(page, size)));
    }
}
