package com.example.taskflow.controller;

import java.util.List;
import java.util.stream.Collectors;

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
import com.example.taskflow.dto.CreateTeamRequestDTO;
import com.example.taskflow.dto.TeamMemberRequestDTO;
import com.example.taskflow.dto.TeamResponseDTO;
import com.example.taskflow.dto.UserSummaryDTO;
import com.example.taskflow.service.TeamService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/organizations", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class OrganizationTeamController {

    private final TeamService teamService;
    private final UserService userService;

    public OrganizationTeamController(TeamService teamService, UserService userService) {
        this.teamService = teamService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
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

    @GetMapping("/teams/{teamId}/observers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<UserSummaryDTO>> getTeamObservers(
            @PathVariable @Min(1) Long teamId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        List<User> observers = teamService.getTeamObservers(teamId, user);
        List<UserSummaryDTO> dtos = observers.stream()
                .map(u -> new UserSummaryDTO(u.getId(), u.getUsername()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/teams/{teamId}/observers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> addTeamObserver(
            @PathVariable @Min(1) Long teamId,
            @RequestBody TeamMemberRequestDTO body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        teamService.addObserver(teamId, body.getUserId(), user);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/teams/{teamId}/observers/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeTeamObserver(
            @PathVariable @Min(1) Long teamId,
            @PathVariable @Min(1) Long userId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        teamService.removeObserver(teamId, userId, user);
        return ResponseEntity.noContent().build();
    }
}
