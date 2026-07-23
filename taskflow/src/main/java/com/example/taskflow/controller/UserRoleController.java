package com.example.taskflow.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.service.RoleService;
import com.example.taskflow.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping(value = "/api/v1/admin", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class UserRoleController {

    private final RoleService roleService;
    private final UserService userService;

    public UserRoleController(RoleService roleService, UserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasPermission(null, 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Set<RoleResponseDTO>> getUserRoles(@PathVariable Long userId) {
        return ResponseEntity.ok(roleService.getUserRoles(userId));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasPermission(null, 'ROLE_MANAGE') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Set<RoleResponseDTO>> assignUserRoles(
            @PathVariable Long userId,
            @RequestBody @NotEmpty List<String> roleNames,
            @AuthenticationPrincipal UserDetails principal) {
        
        com.example.taskflow.domain.User caller = userService.getCurrentUser(principal.getUsername());
        return ResponseEntity.ok(roleService.assignUserRoles(userId, roleNames, caller));
    }
}
