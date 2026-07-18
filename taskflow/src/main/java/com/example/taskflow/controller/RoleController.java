package com.example.taskflow.controller;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.dto.PermissionResponseDTO;
import com.example.taskflow.dto.RoleCreateRequestDTO;
import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.dto.RoleUpdateRequestDTO;
import com.example.taskflow.service.RoleService;
import com.example.taskflow.service.UserService;
import com.example.taskflow.domain.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/admin", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class RoleController {

    private final RoleService roleService;
    private final UserService userService;

    public RoleController(RoleService roleService, UserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    // --- Roles ---

    @GetMapping("/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<RoleResponseDTO>> getAllRoles() {
        return ResponseEntity.ok(roleService.getAllRoles());
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleResponseDTO> createRole(
            @RequestBody @Valid RoleCreateRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        RoleResponseDTO created = roleService.createRole(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<RoleResponseDTO> updateRole(
            @PathVariable Long id, 
            @RequestBody @Valid RoleUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(roleService.updateRole(id, request, caller));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        roleService.deleteRole(id, caller);
        return ResponseEntity.noContent().build();
    }

    // --- Permissions ---

    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionResponseDTO>> getAllPermissions() {
        return ResponseEntity.ok(roleService.getAllPermissions());
    }

    @GetMapping("/roles/{id}/permissions")
    public ResponseEntity<Set<PermissionResponseDTO>> getRolePermissions(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    public ResponseEntity<Set<PermissionResponseDTO>> assignRolePermissions(
            @PathVariable Long id, 
            @RequestBody @Valid AssignPermissionsRequestDTO request,
            @AuthenticationPrincipal UserDetails principal) {
        User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(roleService.assignRolePermissions(id, request, caller));
    }
}
