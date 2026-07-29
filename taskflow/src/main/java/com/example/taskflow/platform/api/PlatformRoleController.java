package com.example.taskflow.platform.api;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.organization.rbac.dto.PermissionResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleCreateRequestDTO;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleUpdateRequestDTO;
import com.example.taskflow.organization.rbac.application.RoleService;
import com.example.taskflow.user.application.UserService;

import com.example.taskflow.security.platform.PlatformAuthorize;
import com.example.taskflow.security.platform.PlatformPermission;

import jakarta.validation.Valid;

/**
 * Platform Control Plane role and permission governance endpoints.
 * Maintained in platform namespace per ADR-009 for super-admin governance and future extensibility.
 */
@RestController
@RequestMapping(value = "/api/v1/platform", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class PlatformRoleController {

    private final RoleService roleService;
    private final UserService userService;

    public PlatformRoleController(RoleService roleService, UserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    @GetMapping("/roles")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_VIEW)
    public ResponseEntity<List<RoleResponseDTO>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @PostMapping("/roles")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_UPDATE)
    public ResponseEntity<RoleResponseDTO> createRole(
            @Valid @RequestBody RoleCreateRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(roleService.createRole(request, caller));
    }

    @PutMapping("/roles/{id}")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_UPDATE)
    public ResponseEntity<RoleResponseDTO> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(roleService.updateRole(id, request, caller));
    }

    @DeleteMapping("/roles/{id}")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_UPDATE)
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        roleService.deleteRole(id, caller);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_VIEW)
    public ResponseEntity<List<PermissionResponseDTO>> getAllPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions());
    }

    @GetMapping("/roles/{id}/permissions")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_VIEW)
    public ResponseEntity<Set<PermissionResponseDTO>> getRolePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @PlatformAuthorize(PlatformPermission.PLATFORM_SETTINGS_UPDATE)
    public ResponseEntity<Set<PermissionResponseDTO>> assignRolePermissions(
            @PathVariable Long id,
            @Valid @RequestBody AssignPermissionsRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(roleService.assignRolePermissions(id, request, caller));
    }
}