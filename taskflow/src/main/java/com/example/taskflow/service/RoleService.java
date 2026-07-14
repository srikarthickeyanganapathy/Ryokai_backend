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
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.User;

import jakarta.transaction.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditService auditService;

    // Define core roles that cannot be renamed
    private static final Set<String> CORE_ROLES = Set.of("SUPER_ADMIN", "DIRECTOR", "MANAGER", "ADMIN", "EMPLOYEE");

    public RoleService(RoleRepository roleRepository, 
                       PermissionRepository permissionRepository,
                       PermissionService permissionService,
                       UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.auditService = auditService;
    }

    private PermissionResponseDTO mapToPermissionResponseDTO(Permission p) {
        return new PermissionResponseDTO(p.getId(), p.getName(), p.getDescription());
    }

    public RoleResponseDTO mapToRoleResponseDTO(Role r) {
        Set<PermissionResponseDTO> perms = r.getPermissions() != null 
            ? r.getPermissions().stream().map(this::mapToPermissionResponseDTO).collect(Collectors.toSet())
            : new HashSet<>();
        return new RoleResponseDTO(r.getId(), r.getName(), r.getDescription(), perms,
                r.getOrganization() != null ? r.getOrganization().getId() : null,
                r.getOrganization() != null ? r.getOrganization().getName() : null);
    }

    public List<RoleResponseDTO> getAllRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream()
            .map(this::mapToRoleResponseDTO)
            .collect(Collectors.toList());
    }

    public List<RoleResponseDTO> getRolesByOrganizationId(Long organizationId) {
        return roleRepository.findByOrganizationId(organizationId).stream()
            .map(this::mapToRoleResponseDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public RoleResponseDTO createRole(RoleCreateRequestDTO request, User caller) {
        // RB-M06 fix: block reserved builtin role names. Previously an org admin
        // could create a custom role named "ADMIN" in their org, which would then
        // be indistinguishable from the builtin ADMIN role in name-based
        // isBuiltinAdmin() checks. The CORE_ROLES set already exists for update
        // guarding — we reuse it here for creation guarding.
        if (CORE_ROLES.contains(request.name().toUpperCase())) {
            throw new IllegalArgumentException(
                "Role name '" + request.name() + "' is reserved. Choose a different name.");
        }

        if (request.organizationId() != null) {
            if (roleRepository.findByNameAndOrganizationId(request.name(), request.organizationId()).isPresent()) {
                throw new IllegalArgumentException("Role already exists in this organization");
            }
        } else {
            if (roleRepository.findByNameAndOrganizationIdIsNull(request.name()).isPresent()) {
                throw new IllegalArgumentException("Global role already exists");
            }
        }

        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        
        if (request.organizationId() != null) {
            Organization org = organizationRepository.findById(request.organizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
            role.setOrganization(org);
        }
        
        Role saved = roleRepository.save(role);

        auditService.record("ROLE_CREATED", caller, "ROLE", saved.getId(),
                null, mapToRoleResponseDTO(saved), "Created role: " + saved.getName());

        return mapToRoleResponseDTO(saved);
    }

    @Transactional
    public RoleResponseDTO updateRole(Long id, RoleUpdateRequestDTO request, User caller) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        RoleResponseDTO oldValue = mapToRoleResponseDTO(role);
        
        if (request.name() != null && !role.getName().equals(request.name())) {
            // Guard against renaming core roles
            if (CORE_ROLES.contains(role.getName())) {
                throw new IllegalArgumentException("Cannot rename core system roles");
            }

            if (role.getOrganization() != null) {
                roleRepository.findByNameAndOrganizationId(request.name(), role.getOrganization().getId()).ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("Role name already exists in this organization");
                    }
                });
            } else {
                roleRepository.findByNameAndOrganizationIdIsNull(request.name()).ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("Global role name already exists");
                    }
                });
            }
            role.setName(request.name());
        }
        
        if (request.description() != null) { 
            role.setDescription(request.description()); 
        }
        
        Role saved = roleRepository.save(role);
        
        auditService.record("ROLE_UPDATED", caller, "ROLE", saved.getId(),
                oldValue, mapToRoleResponseDTO(saved), "Updated role: " + saved.getName());
                
        return mapToRoleResponseDTO(saved);
    }

    @Transactional
    public void deleteRole(Long id, User caller) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        if (CORE_ROLES.contains(role.getName())) {
            throw new IllegalArgumentException("Cannot delete built-in system role: " + role.getName());
        }
        
        if (userRepository.existsByRolesId(role.getId())) {
            throw new IllegalStateException("Cannot delete a role that is still assigned to users");
        }

        RoleResponseDTO oldValue = mapToRoleResponseDTO(role);
        List<User> holders = userRepository.findAllByRolesId(role.getId());
        roleRepository.delete(role);
        holders.forEach(u -> permissionService.invalidateCache(u.getId()));
        
        auditService.record("ROLE_DELETED", caller, "ROLE", id,
                oldValue, null, "Deleted role: " + role.getName());
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
    public Set<PermissionResponseDTO> assignRolePermissions(Long id, AssignPermissionsRequestDTO request, User caller) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        boolean callerIsSuperAdmin = caller.getRoles().stream()
            .anyMatch(r -> r.getName().endsWith("SUPER_ADMIN"));
            
        if (!callerIsSuperAdmin) {
            // For org-scoped roles: the caller was already verified as org admin by
            // OrganizationService.requireAdminMembership() before this method is called.
            // Org admins can grant ANY permission to roles within their organization.
            boolean isOrgScopedRole = role.getOrganization() != null;
            
            if (!isOrgScopedRole) {
                // For global roles: enforce "you can only grant permissions you hold"
                Set<String> callerPerms = permissionService.getPermissionsForUser(caller);
                for (String pName : request.permissionNames()) {
                    if (!callerPerms.contains(pName)) {
                        throw new org.springframework.security.access.AccessDeniedException(
                            "You may only grant permissions you currently hold: " + pName);
                    }
                }
                if (CORE_ROLES.contains(role.getName())) {
                    throw new org.springframework.security.access.AccessDeniedException(
                        "Built-in role permissions may only be modified by SUPER_ADMIN");
                }
            }
        }
        
        Set<Permission> oldPerms = new HashSet<>(role.getPermissions());
        Set<Permission> updatedPermissions = new HashSet<>();
        for (String pName : request.permissionNames()) {
            Permission permission = permissionRepository.findByName(pName)
                    .orElseThrow(() -> new RuntimeException("Permission not found: " + pName));
            updatedPermissions.add(permission);
        }
        
        role.setPermissions(updatedPermissions);
        roleRepository.save(role);

        permissionService.invalidateAll();
        
        Set<PermissionResponseDTO> newPermsDTO = role.getPermissions().stream()
            .map(this::mapToPermissionResponseDTO).collect(Collectors.toSet());
            
        auditService.record("ROLE_PERMISSIONS_CHANGED", caller, "ROLE", id,
                oldPerms.stream().map(Permission::getName).collect(Collectors.toList()),
                newPermsDTO.stream().map(PermissionResponseDTO::getName).collect(Collectors.toList()),
                "Updated permissions for role: " + role.getName());
        
        return newPermsDTO;
    }
}
