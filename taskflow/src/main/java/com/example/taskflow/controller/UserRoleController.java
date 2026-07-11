package com.example.taskflow.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.PermissionResponseDTO;
import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.repository.RoleRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.service.UserService;
import com.example.taskflow.service.PermissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping(value = "/api/admin", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class UserRoleController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final PermissionService permissionService;
    private final com.example.taskflow.service.AuditService auditService;

    public UserRoleController(UserRepository userRepository, RoleRepository roleRepository, UserService userService, PermissionService permissionService, com.example.taskflow.service.AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasPermission(null, 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Set<RoleResponseDTO>> getUserRoles(@PathVariable Long userId) {
        User user = userService.getUserById(userId);
        Set<RoleResponseDTO> roles = user.getRoles().stream()
                .map(r -> {
                    Set<PermissionResponseDTO> perms = r.getPermissions().stream()
                            .map(p -> new PermissionResponseDTO(p.getId(), p.getName(), p.getDescription()))
                            .collect(Collectors.toSet());
                    return new RoleResponseDTO(r.getId(), r.getName(), r.getDescription(), perms);
                })
                .collect(Collectors.toSet());
        return ResponseEntity.ok(roles);
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasPermission(null, 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Set<RoleResponseDTO>> assignUserRoles(
            @PathVariable Long userId,
            @RequestBody @NotEmpty List<String> roleNames,
            @AuthenticationPrincipal UserDetails principal) {
        
        User caller = userService.getCurrentUser(principal.getUsername());
        boolean callerIsSuperAdmin = caller.getRoles().stream()
            .anyMatch(r -> r.getName().endsWith("SUPER_ADMIN"));

        boolean touchesSuperAdmin = roleNames.stream()
            .anyMatch(n -> n.replaceFirst("^ROLE_", "").equals("SUPER_ADMIN"));
        if (touchesSuperAdmin && !callerIsSuperAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("Only SUPER_ADMIN may assign the SUPER_ADMIN role");
        }

        // Global roles can ONLY be SUPER_ADMIN.
        // Org-scoped roles (ADMIN, DIRECTOR, MANAGER, EMPLOYEE) are assigned
        // through OrganizationMembership, not through the global user_roles table.
        for (String requested : roleNames) {
            String normalized = requested.replaceFirst("^ROLE_", "");
            if (!normalized.equals("SUPER_ADMIN")) {
                throw new IllegalArgumentException(
                    "Only SUPER_ADMIN can be assigned as a global role. " +
                    "Use organization membership to assign org roles (ADMIN, DIRECTOR, MANAGER, EMPLOYEE).");
            }
        }

        User user = userService.getUserById(userId);
        
        Set<String> oldRoles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        
        Set<Role> newRoles = roleNames.stream()
                .map(name -> roleRepository.findByName(name.startsWith("ROLE_") ? name : "ROLE_" + name)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name)))
                .collect(Collectors.toSet());
        user.setRoles(newRoles);
        userRepository.save(user);
        
        permissionService.invalidateCache(user.getId());
        
        auditService.record("USER_ROLES_ASSIGNED", caller, "USER", user.getId(),
                oldRoles, newRoles.stream().map(Role::getName).collect(Collectors.toSet()),
                "Assigned roles to user " + user.getUsername());

        Set<RoleResponseDTO> response = newRoles.stream()
                .map(r -> {
                    Set<PermissionResponseDTO> perms = r.getPermissions().stream()
                            .map(p -> new PermissionResponseDTO(p.getId(), p.getName(), p.getDescription()))
                            .collect(Collectors.toSet());
                    return new RoleResponseDTO(r.getId(), r.getName(), r.getDescription(), perms);
                })
                .collect(Collectors.toSet());
        return ResponseEntity.ok(response);
    }
}
