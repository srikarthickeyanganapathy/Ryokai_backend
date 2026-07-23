package com.example.taskflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.InviteMemberRequestDTO;
import com.example.taskflow.dto.OrganizationInviteDTO;
import com.example.taskflow.service.OrganizationInviteService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class OrganizationInviteController {

    private final OrganizationInviteService inviteService;
    private final UserService userService;

    public OrganizationInviteController(OrganizationInviteService inviteService, UserService userService) {
        this.inviteService = inviteService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping("/organizations/{orgId}/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInviteDTO> sendInvite(
            @PathVariable @Min(1) Long orgId,
            @Valid @RequestBody InviteMemberRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        User invitee = userService.getCurrentUser(request.getUsername());
        OrganizationInviteDTO invite = inviteService.createInAppInvite(
                orgId, invitee.getId(), request.getRoleId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(invite);
    }

    @PostMapping("/organizations/{orgId}/invites/link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInviteDTO> createShareableLink(
            @PathVariable @Min(1) Long orgId,
            @Valid @RequestBody com.example.taskflow.dto.UpdateRoleRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        OrganizationInviteDTO invite = inviteService.createShareableLink(
                orgId, request.getRoleId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(invite);
    }

    @GetMapping("/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationInviteDTO>> getMyInvites(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(inviteService.getMyPendingInvites(user));
    }

    @PostMapping("/invites/{inviteId}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInviteDTO> acceptInvite(
            @PathVariable @Min(1) Long inviteId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(inviteService.acceptInvite(inviteId, user));
    }

    @PostMapping("/invites/{inviteId}/decline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInviteDTO> declineInvite(
            @PathVariable @Min(1) Long inviteId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(inviteService.declineInvite(inviteId, user));
    }
    @PostMapping("/invites/token/{token}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInviteDTO> acceptInviteByToken(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(inviteService.acceptInviteByToken(token, user));
    }
}
