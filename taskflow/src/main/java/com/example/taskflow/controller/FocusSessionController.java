package com.example.taskflow.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.FocusSessionDTOs.FocusSessionResponseDTO;
import com.example.taskflow.dto.FocusSessionDTOs.FocusSessionStartRequestDTO;
import com.example.taskflow.service.FocusSessionService;
import com.example.taskflow.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/focus", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class FocusSessionController {

    private final FocusSessionService focusSessionService;
    private final UserService userService;

    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FocusSessionResponseDTO> start(
            @RequestBody(required = false) FocusSessionStartRequestDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        Long taskId = body != null ? body.getTaskId() : null;
        return ResponseEntity.ok(focusSessionService.start(user, taskId));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FocusSessionResponseDTO> stop(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(focusSessionService.stop(user, id));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FocusSessionResponseDTO> active(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return focusSessionService.getActive(user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<FocusSessionResponseDTO>> history(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        Pageable capped = pageable.getPageSize() > 100
                ? PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort())
                : pageable;
        return ResponseEntity.ok(focusSessionService.getHistory(user, capped));
    }
}
