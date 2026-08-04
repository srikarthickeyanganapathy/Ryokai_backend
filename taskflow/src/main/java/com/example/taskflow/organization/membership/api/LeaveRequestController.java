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

import com.example.taskflow.organization.membership.application.LeaveRequestService;
import com.example.taskflow.organization.membership.dto.CreateLeaveRequestDTO;
import com.example.taskflow.organization.membership.dto.LeaveRequestDTO;
import com.example.taskflow.user.application.UserService;
import com.example.taskflow.user.domain.User;

import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/organizations/{id}/leave-requests", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final UserService userService;

    public LeaveRequestController(LeaveRequestService leaveRequestService, UserService userService) {
        this.leaveRequestService = leaveRequestService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> requestLeave(
            @PathVariable @Min(1) Long id,
            @RequestBody(required = false) CreateLeaveRequestDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        LeaveRequestDTO response = leaveRequestService.requestLeave(id, user, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasPermission(#id, 'Organization', 'LEAVE_VIEW') or hasRole('SUPER_ADMIN') or isAuthenticated()")
    public ResponseEntity<List<LeaveRequestDTO>> listLeaveRequests(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(leaveRequestService.listLeaveRequests(id, user));
    }

    @GetMapping("/status")
    @PreAuthorize("hasPermission(#id, 'Organization', 'LEAVE_VIEW') or hasRole('SUPER_ADMIN') or isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> getLeaveRequestStatus(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(leaveRequestService.getLeaveRequestStatus(id, user));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasPermission(#id, 'Organization', 'LEAVE_APPROVE') or hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<LeaveRequestDTO> approveLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(leaveRequestService.approveLeave(id, requestId, user));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasPermission(#id, 'Organization', 'LEAVE_APPROVE') or hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<LeaveRequestDTO> rejectLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        String comment = body != null ? body.get("comment") : null;
        return ResponseEntity.ok(leaveRequestService.rejectLeave(id, requestId, user, comment));
    }

    @PostMapping("/{requestId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> cancelLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(leaveRequestService.cancelLeave(id, requestId, user));
    }
}
