package com.example.taskflow.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.Permission;
import com.example.taskflow.domain.Role;
import com.example.taskflow.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.dto.PermissionResponseDTO;
import com.example.taskflow.dto.RoleCreateRequestDTO;
import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.dto.RoleUpdateRequestDTO;
import com.example.taskflow.repository.PermissionRepository;
import com.example.taskflow.repository.RoleRepository;

import jakarta.transaction.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;

    // Define core roles that cannot be renamed
    private static final Set<String> CORE_ROLES = Set.of("SUPER_ADMIN", "DIRECTOR", "MANAGER", "ADMIN", "EMPLOYEE");

    public RoleService(RoleRepository roleRepository, 
                       PermissionRepository permissionRepository,
                       PermissionService permissionService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
    }

    private PermissionResponseDTO mapToPermissionResponseDTO(Permission p) {
        return new PermissionResponseDTO(p.getId(), p.getName(), p.getDescription());
    }

    private RoleResponseDTO mapToRoleResponseDTO(Role r) {
        Set<PermissionResponseDTO> perms = r.getPermissions() != null 
            ? r.getPermissions().stream().map(this::mapToPermissionResponseDTO).collect(Collectors.toSet())
            : new HashSet<>();
        return new RoleResponseDTO(r.getId(), r.getName(), r.getDescription(), perms);
    }

    public List<RoleResponseDTO> getAllRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream()
            .map(this::mapToRoleResponseDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public RoleResponseDTO createRole(RoleCreateRequestDTO request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("Role already exists");
        }

        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description()); 
        Role saved = roleRepository.save(role);

        return mapToRoleResponseDTO(saved);
    }

    @Transactional
    public RoleResponseDTO updateRole(Long id, RoleUpdateRequestDTO request) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        if (request.name() != null && !role.getName().equals(request.name())) {
            // Guard against renaming core roles
            if (CORE_ROLES.contains(role.getName())) {
                throw new IllegalArgumentException("Cannot rename core system roles");
            }

            roleRepository.findByName(request.name()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Role name already exists");
                }
            });
            role.setName(request.name());
        }
        
        if (request.description() != null) { 
            role.setDescription(request.description()); 
        }
        
        Role saved = roleRepository.save(role);
        return mapToRoleResponseDTO(saved);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        if (role.getName().equals("SUPER_ADMIN") || role.getName().equals("EMPLOYEE")) {
            throw new IllegalArgumentException("Cannot delete core system roles");
        }

        roleRepository.delete(role);
    }

    public List<PermissionResponseDTO> getAllPermissions() {
        return permissionRepository.findAllByOrderByNameAsc().stream()
            .map(this::mapToPermissionResponseDTO)
            .collect(Collectors.toList());
    }

    public Set<PermissionResponseDTO> getRolePermissions(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        return role.getPermissions().stream()
            .map(this::mapToPermissionResponseDTO).collect(Collectors.toSet());
    }

    @Transactional
    public Set<PermissionResponseDTO> assignRolePermissions(Long id, AssignPermissionsRequestDTO request) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        Set<Permission> updatedPermissions = new HashSet<>();
        for (String pName : request.permissionNames()) {
            Permission permission = permissionRepository.findByName(pName)
                    .orElseThrow(() -> new RuntimeException("Permission not found: " + pName));
            updatedPermissions.add(permission);
        }
        
        role.setPermissions(updatedPermissions);
        roleRepository.save(role);

        permissionService.invalidateAll();
        
        return role.getPermissions().stream()
            .map(this::mapToPermissionResponseDTO).collect(Collectors.toSet());
    }
}
