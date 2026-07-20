package com.example.taskflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import java.util.List;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ChecklistItemDTO;
import com.example.taskflow.dto.ChecklistItemRequestDTO;
import com.example.taskflow.service.ChecklistService;
import com.example.taskflow.service.UserService;

@RestController
@RequestMapping(value = "/api/tasks", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class TaskChecklistController {

    private final ChecklistService checklistService;
    private final UserService userService;

    public TaskChecklistController(
            ChecklistService checklistService,
            UserService userService) {
        this.checklistService = checklistService;
        this.userService = userService;
    }

    // Helper to get currently logged-in user securely
    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping("/{taskId}/checklists")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<ChecklistItemDTO> addChecklistItem(@PathVariable @Min(1) Long taskId,
            @Valid @RequestBody ChecklistItemRequestDTO request, @AuthenticationPrincipal UserDetails userDetails) {
        ChecklistItemDTO response = checklistService.addChecklistItem(taskId, request.getText(),
                getCurrentUser(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{taskId}/checklists/{itemId}/toggle")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<ChecklistItemDTO> toggleChecklistItem(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long itemId, @AuthenticationPrincipal UserDetails userDetails) {
        ChecklistItemDTO response = checklistService.toggleChecklistItem(taskId, itemId, getCurrentUser(userDetails));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{taskId}/checklists/{itemId}")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<Void> deleteChecklistItem(@PathVariable @Min(1) Long taskId,
            @PathVariable @Min(1) Long itemId, @AuthenticationPrincipal UserDetails userDetails) {
        checklistService.deleteChecklistItem(taskId, itemId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{taskId}/checklists/order")
    @PreAuthorize("hasPermission(#taskId, 'Task', 'EDIT')")
    public ResponseEntity<Void> reorderChecklistItems(@PathVariable @Min(1) Long taskId,
            @RequestBody List<Long> itemIds, @AuthenticationPrincipal UserDetails userDetails) {
        checklistService.reorderChecklistItems(taskId, itemIds, getCurrentUser(userDetails));
        return ResponseEntity.ok().build();
    }
}
