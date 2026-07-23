package com.example.taskflow.controller;

import java.util.List;
import java.util.Set;

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
import com.example.taskflow.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.dto.PermissionResponseDTO;
import com.example.taskflow.dto.RoleCreateRequestDTO;
import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.dto.RoleUpdateRequestDTO;
import com.example.taskflow.service.RoleService;
import com.example.taskflow.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping(value = "/api/v1/organizations", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class OrganizationRoleController {

    private final RoleService roleService;
    private final UserService userService;

    public OrganizationRoleController(RoleService roleService, UserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasPermission(#id, 'Organization', 'MEMBER') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<RoleResponseDTO>> listRoles(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(roleService.getRolesByOrganizationId(id));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<RoleResponseDTO> createRole(
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody RoleCreateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        RoleCreateRequestDTO orgRequest = new RoleCreateRequestDTO(
            request.name(), request.description(), id, request.priority());
        RoleResponseDTO response = roleService.createRole(orgRequest, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<RoleResponseDTO> updateRole(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long roleId,
            @Valid @RequestBody RoleUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        RoleResponseDTO response = roleService.updateRole(roleId, request, user);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteRole(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long roleId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        roleService.deleteRole(roleId, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles/{roleId}/permissions")
    @PreAuthorize("hasPermission(#id, 'Organization', 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Set<PermissionResponseDTO>> updateRolePermissions(
            @PathVariable @Min(1) Long id,
            @PathVariable @Min(1) Long roleId,
            @Valid @RequestBody AssignPermissionsRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        Set<PermissionResponseDTO> response = roleService.assignRolePermissions(roleId, request, user);
        return ResponseEntity.ok(response);
    }
}
