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

import com.example.taskflow.dto.ActivityLogDTO;
import com.example.taskflow.service.ActivityLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskActivityController {

    private final ActivityLogService activityLogService;

    /**
     * @deprecated Task activity logs are consumed via paged task history in TaskController
     * (GET /api/v1/tasks/{id}/history).
     */
    @Deprecated
    @GetMapping("/{taskId}/activities")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    public ResponseEntity<Page<ActivityLogDTO>> getTaskActivities(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(activityLogService.getTaskActivityLogs(taskId, PageRequest.of(page, size)));
    }
}
