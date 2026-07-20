package com.example.taskflow.controller;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.WorkloadDTOs.UserWorkloadDTO;
import com.example.taskflow.service.UserService;
import com.example.taskflow.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/organizations/{orgId}/workload", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService workloadService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserWorkloadDTO>> matrix(
            @PathVariable Long orgId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(workloadService.getWorkloadMatrix(user, orgId));
    }
}
