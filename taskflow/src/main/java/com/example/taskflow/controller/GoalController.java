package com.example.taskflow.controller;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.GoalDTOs.GoalRequestDTO;
import com.example.taskflow.dto.GoalDTOs.GoalResponseDTO;
import com.example.taskflow.service.GoalService;
import com.example.taskflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/organizations/{orgId}/goals", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GoalResponseDTO>> list(@PathVariable Long orgId) {
        return ResponseEntity.ok(goalService.getGoals(orgId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GoalResponseDTO> create(@PathVariable Long orgId, @RequestBody GoalRequestDTO req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(goalService.create(user, orgId, req));
    }

    @PutMapping("/{goalId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GoalResponseDTO> update(@PathVariable Long orgId, @PathVariable Long goalId,
            @RequestBody GoalRequestDTO req, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(goalService.update(user, goalId, req));
    }

    @DeleteMapping("/{goalId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long orgId, @PathVariable Long goalId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        goalService.delete(user, goalId);
        return ResponseEntity.noContent().build();
    }
}
