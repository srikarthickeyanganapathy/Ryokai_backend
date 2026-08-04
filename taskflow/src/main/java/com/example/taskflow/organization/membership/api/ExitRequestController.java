package com.example.taskflow.organization.membership.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.organization.membership.application.ExitRequestService;
import com.example.taskflow.organization.membership.dto.CreateExitRequestDTO;
import com.example.taskflow.organization.membership.dto.ExitBlockersDTO;
import com.example.taskflow.organization.membership.dto.ExitRequestDTO;
import com.example.taskflow.user.application.UserService;
import com.example.taskflow.user.domain.User;

import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/organizations/{id}/exit-requests", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class ExitRequestController {

    private final ExitRequestService exitRequestService;
    private final UserService userService;

    public ExitRequestController(ExitRequestService exitRequestService, UserService userService) {
        this.exitRequestService = exitRequestService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping("/blockers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExitBlockersDTO> getExitBlockers(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(exitRequestService.getExitBlockers(id, user));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExitRequestDTO> requestExit(
            @PathVariable @Min(1) Long id,
            @RequestBody(required = false) CreateExitRequestDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        ExitRequestDTO response = exitRequestService.requestExit(id, user, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ExitRequestDTO>> listExitRequests(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(exitRequestService.listExitRequests(id, user));
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExitRequestDTO> getExitRequestStatus(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(exitRequestService.getExitRequestStatus(id, user));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasPermission(#id, 'Organization', 'EXIT_REQUEST_APPROVE') or hasPermission(#id, 'Organization', 'MEMBER_EXIT_APPROVE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ExitRequestDTO> approveExit(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @RequestParam(defaultValue = "false") boolean offboarding,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(exitRequestService.approveExit(id, requestId, user, offboarding));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasPermission(#id, 'Organization', 'EXIT_REQUEST_REJECT') or hasPermission(#id, 'Organization', 'MEMBER_EXIT_APPROVE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ExitRequestDTO> rejectExit(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(exitRequestService.rejectExit(id, requestId, user, comment));
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExitRequestDTO> cancelExit(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(exitRequestService.cancelExit(id, requestId, user));
    }
}
