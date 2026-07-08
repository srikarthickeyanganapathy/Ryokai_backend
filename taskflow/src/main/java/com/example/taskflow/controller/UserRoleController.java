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

import jakarta.validation.constraints.NotEmpty;

@RestController
@RequestMapping(value = "/api/admin", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class UserRoleController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;

    public UserRoleController(UserRepository userRepository, RoleRepository roleRepository, UserService userService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
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
            @RequestBody @NotEmpty List<String> roleNames) {
        User user = userService.getUserById(userId);
        Set<Role> newRoles = roleNames.stream()
                .map(name -> roleRepository.findByName(name.startsWith("ROLE_") ? name : "ROLE_" + name)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name)))
                .collect(Collectors.toSet());
        user.setRoles(newRoles);
        userRepository.save(user);

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
