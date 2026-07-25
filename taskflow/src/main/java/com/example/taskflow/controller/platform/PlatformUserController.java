package com.example.taskflow.controller.platform;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.service.platform.PlatformUserService;
import com.example.taskflow.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.validation.constraints.NotEmpty;

/**
 * Platform Control Plane user governance endpoints.
 * Migrated from UserRoleController to dedicated platform package and namespace per ADR-009.
 */
@RestController
@RequestMapping(value = "/api/v1/platform", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformUserController {

    private final PlatformUserService platformUserService;
    private final UserService userService;

    public PlatformUserController(PlatformUserService platformUserService, UserService userService) {
        this.platformUserService = platformUserService;
        this.userService = userService;
    }

    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<Set<RoleResponseDTO>> getUserRoles(@PathVariable Long userId) {
        return ResponseEntity.ok(platformUserService.getUserRoles(userId));
    }

    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<Set<RoleResponseDTO>> assignUserRoles(
            @PathVariable Long userId,
            @RequestBody @NotEmpty List<String> roleNames,
            @AuthenticationPrincipal UserDetails principal) {
        
        com.example.taskflow.domain.User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(platformUserService.assignUserRoles(userId, roleNames, caller));
    }
}
