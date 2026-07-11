package com.example.taskflow.controller;


import com.example.taskflow.domain.User;
import com.example.taskflow.dto.CreateOrganizationRequestDTO;
import com.example.taskflow.dto.CreateTeamRequestDTO;
import com.example.taskflow.dto.InviteMemberRequestDTO;
import com.example.taskflow.dto.LeaveRequestDTO;
import com.example.taskflow.dto.MembershipResponseDTO;
import com.example.taskflow.dto.TeamMemberRequestDTO;
import com.example.taskflow.dto.LeaveReasonDTO;
import com.example.taskflow.dto.LeaveRejectDTO;
import com.example.taskflow.dto.OrganizationResponseDTO;
import com.example.taskflow.dto.TeamResponseDTO;
import com.example.taskflow.service.OrganizationService;
import com.example.taskflow.service.OrganizationInviteService;
import com.example.taskflow.service.TeamService;
import com.example.taskflow.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/organizations", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;
    private final TeamService teamService;
    private final UserService userService;

    private final OrganizationInviteService inviteService;

    public OrganizationController(OrganizationService organizationService,
                                  TeamService teamService,
                                  UserService userService,
                                  OrganizationInviteService inviteService) {
        this.organizationService = organizationService;
        this.teamService = teamService;
        this.userService = userService;
        this.inviteService = inviteService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponseDTO> createOrganization(
            @Valid @RequestBody CreateOrganizationRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        OrganizationResponseDTO response = organizationService.createOrganization(
                request.getName(), request.getDescription(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationResponseDTO>> listOrganizations(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.listUserOrganizations(user.getId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponseDTO> getOrganization(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.getOrganization(id, user));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.example.taskflow.dto.OrganizationInviteDTO> inviteMember(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody InviteMemberRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        User invitedUser = userService.getCurrentUser(request.getUsername());
        com.example.taskflow.dto.OrganizationInviteDTO invite = inviteService.createInAppInvite(
                id, invitedUser.getId(), request.getRoleId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(invite);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeMember(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        organizationService.removeMember(id, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members/{userId}/role")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MembershipResponseDTO> updateMemberRole(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long userId,
            @Valid @RequestBody com.example.taskflow.dto.UpdateRoleRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        MembershipResponseDTO response = organizationService.updateMemberRole(
                id, userId, request.getRoleId(), currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MembershipResponseDTO>> listMembers(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.listOrganizationMembers(id, user));
    }

    @PostMapping("/{id}/teams")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponseDTO> createTeam(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody CreateTeamRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        TeamResponseDTO response = teamService.createTeam(
                id, request.getName(), request.getDescription(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/teams")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamResponseDTO>> listTeams(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(teamService.listOrgTeams(id, user));
    }

    @PostMapping("/teams/{teamId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponseDTO> addTeamMember(
            @PathVariable @Min(1) Long teamId,
            @Valid @RequestBody TeamMemberRequestDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        TeamResponseDTO response = teamService.addTeamMember(teamId, body.getUserId(), currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/teams/{teamId}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponseDTO> removeTeamMember(
            @PathVariable @Min(1) Long teamId,
            @PathVariable @Min(1) Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        TeamResponseDTO response = teamService.removeTeamMember(teamId, userId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/teams/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponseDTO> getTeam(
            @PathVariable @Min(1) Long teamId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(teamService.getTeam(teamId, user));
    }

    @PutMapping("/teams/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TeamResponseDTO> updateTeam(
            @PathVariable @Min(1) Long teamId,
            @Valid @RequestBody CreateTeamRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        TeamResponseDTO response = teamService.updateTeam(
                teamId, request.getName(), request.getDescription(), user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/teams/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable @Min(1) Long teamId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        teamService.deleteTeam(teamId, user);
        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // LEAVE REQUEST ENDPOINTS
    // ========================================================================

    @PostMapping("/{id}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> requestLeave(
            @PathVariable @Min(1) Long id,
            @RequestBody(required = false) LeaveReasonDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        String reason = body != null ? body.getReason() : null;
        LeaveRequestDTO response = organizationService.requestLeave(id, user, reason);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/leave/{requestId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> approveLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        LeaveRequestDTO response = organizationService.approveLeave(id, requestId, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/leave/{requestId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> rejectLeave(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long requestId,
            @RequestBody(required = false) LeaveRejectDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        String comment = body != null ? body.getComment() : null;
        LeaveRequestDTO response = organizationService.rejectLeave(id, requestId, user, comment);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LeaveRequestDTO>> listLeaveRequests(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.listLeaveRequests(id, user));
    }

    @GetMapping("/{id}/leave/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveRequestDTO> getLeaveRequestStatus(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        LeaveRequestDTO response = organizationService.getLeaveRequestStatus(id, user);
        return ResponseEntity.ok(response);  // null means no pending request
    }

    // ========================================================================
    // ROLE MANAGEMENT ENDPOINTS
    // ========================================================================

    @GetMapping("/{id}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<com.example.taskflow.dto.RoleResponseDTO>> listRoles(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(organizationService.listOrganizationRoles(id, user));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.example.taskflow.dto.RoleResponseDTO> createRole(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody com.example.taskflow.dto.RoleCreateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        com.example.taskflow.dto.RoleResponseDTO response = organizationService.createOrganizationRole(id, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/roles/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.example.taskflow.dto.RoleResponseDTO> updateRole(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long roleId,
            @Valid @RequestBody com.example.taskflow.dto.RoleUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        com.example.taskflow.dto.RoleResponseDTO response = organizationService.updateOrganizationRole(id, roleId, request, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteRole(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long roleId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        organizationService.deleteOrganizationRole(id, roleId, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles/{roleId}/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.Set<com.example.taskflow.dto.PermissionResponseDTO>> updateRolePermissions(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long roleId,
            @Valid @RequestBody com.example.taskflow.dto.AssignPermissionsRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        java.util.Set<com.example.taskflow.dto.PermissionResponseDTO> response = organizationService.updateOrganizationRolePermissions(id, roleId, request, user);
        return ResponseEntity.ok(response);
    }
}
