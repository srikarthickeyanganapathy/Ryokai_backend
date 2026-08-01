package com.example.taskflow.goal.api;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.goal.dto.GoalDTOs.GoalRequestDTO;
import com.example.taskflow.goal.dto.GoalDTOs.GoalResponseDTO;
import com.example.taskflow.goal.application.GoalService;
import com.example.taskflow.user.application.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/organizations/{orgId}/goals", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasPermission(#orgId, 'Organization', 'GOAL_VIEW')")
    public ResponseEntity<List<GoalResponseDTO>> list(@PathVariable Long orgId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(goalService.getGoals(orgId, user));
    }

    @PostMapping
    @PreAuthorize("hasPermission(#orgId, 'Organization', 'GOAL_CREATE')")
    public ResponseEntity<GoalResponseDTO> create(@PathVariable Long orgId, @RequestBody GoalRequestDTO req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(goalService.create(user, orgId, req));
    }

    @PutMapping("/{goalId}")
    @PreAuthorize("hasPermission(#goalId, 'Goal', 'GOAL_UPDATE')")
    public ResponseEntity<GoalResponseDTO> update(@PathVariable Long orgId, @PathVariable Long goalId,
            @RequestBody GoalRequestDTO req, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(goalService.update(user, goalId, req));
    }

    @DeleteMapping("/{goalId}")
    @PreAuthorize("hasPermission(#goalId, 'Goal', 'GOAL_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long orgId, @PathVariable Long goalId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        goalService.delete(user, goalId);
        return ResponseEntity.noContent().build();
    }
}
