package com.example.taskflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.InviteMemberRequestDTO;
import com.example.taskflow.dto.LeaveReasonDTO;
import com.example.taskflow.dto.LeaveRejectDTO;
import com.example.taskflow.dto.LeaveRequestDTO;
import com.example.taskflow.dto.MembershipResponseDTO;
import com.example.taskflow.dto.OrganizationInviteDTO;
import com.example.taskflow.service.OrganizationInviteService;
import com.example.taskflow.service.OrganizationLeaveService;
import com.example.taskflow.service.OrganizationLifecycleService;
import com.example.taskflow.service.OrganizationMemberService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/organizations", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class OrganizationMembershipController {

    private final OrganizationMemberService memberService;
    private final OrganizationLeaveService leaveService;
    private final OrganizationLifecycleService lifecycleService;
    private final OrganizationInviteService inviteService;
    private final UserService userService;

    public OrganizationMembershipController(OrganizationMemberService memberService,
                                            OrganizationLeaveService leaveService,
                                            OrganizationLifecycleService lifecycleService,
                                            OrganizationInviteService inviteService,
                                            UserService userService) {
        this.memberService = memberService;
        this.leaveService = leaveService;
        this.lifecycleService = lifecycleService;
        this.inviteService = inviteService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInviteDTO> inviteMember(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody InviteMemberRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        User invitedUser = userService.getCurrentUser(request.getUsername());
        OrganizationInviteDTO invite = inviteService.createInAppInvite(
                id, invitedUser.getId(), request.getRoleId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(invite);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ORG_MEMBER_REMOVE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        memberService.removeMember(id, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{userId}/role")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<MembershipResponseDTO> updateMemberRole(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long userId,
            @Valid @RequestBody com.example.taskflow.dto.UpdateRoleRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        MembershipResponseDTO response = memberService.updateMemberRole(
                id, userId, request.getRoleId(), currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasPermission(#id, 'Organization', 'MEMBER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<MembershipResponseDTO>> listMembers(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(memberService.listOrganizationMembers(id, user));
    }

    @PostMapping("/{id}/leave")
    @PreAuthorize("hasPermission(#id, 'Organization', 'MEMBER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<LeaveRequestDTO> requestLeave(
            @PathVariable @Min(1) Long id,
            @RequestBody(required = false) LeaveReasonDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        String reason = body != null ? body.getReason() : null;
        LeaveRequestDTO response = leaveService.requestLeave(id, user, reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/admin-leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leaveOrDissolveOrganization(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody com.example.taskflow.dto.AdminLeaveRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        lifecycleService.leaveOrDissolve(id, user, request.getSuccessorUserId(), request.isDissolve());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave/{requestId}/approve")
    @PreAuthorize("hasPermission(#id, 'Organization', 'LEAVE_REQUEST_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<LeaveRequestDTO> approveLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        LeaveRequestDTO response = leaveService.approveLeave(id, requestId, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/leave/{requestId}/reject")
    @PreAuthorize("hasPermission(#id, 'Organization', 'LEAVE_REQUEST_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<LeaveRequestDTO> rejectLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @RequestBody(required = false) LeaveRejectDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        String comment = body != null ? body.getComment() : null;
        LeaveRequestDTO response = leaveService.rejectLeave(id, requestId, user, comment);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/leave")
    @PreAuthorize("hasPermission(#id, 'Organization', 'MEMBER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<LeaveRequestDTO>> listLeaveRequests(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(leaveService.listLeaveRequests(id, user));
    }

    @GetMapping("/{id}/leave/status")
    @PreAuthorize("hasPermission(#id, 'Organization', 'MEMBER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<LeaveRequestDTO> getLeaveRequestStatus(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        LeaveRequestDTO response = leaveService.getLeaveRequestStatus(id, user);
        return ResponseEntity.ok(response);
    }
}
