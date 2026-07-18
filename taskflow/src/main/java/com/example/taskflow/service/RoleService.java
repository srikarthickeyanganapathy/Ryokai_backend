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
    private final com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository;
    private final AuditService auditService;

    // Define core roles that cannot be renamed
    private static final Set<String> CORE_ROLES = Set.of("SUPER_ADMIN", "ADMIN");

    public RoleService(RoleRepository roleRepository, 
                       PermissionRepository permissionRepository,
                       PermissionService permissionService,
                       UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       com.example.taskflow.repository.OrganizationMembershipRepository membershipRepository,
                       AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
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
                r.getOrganization() != null ? r.getOrganization().getName() : null,
                r.getPriority());
    }

    private Integer getCallerPriority(User caller, Long orgId) {
        if (orgId != null) {
            Organization org = organizationRepository.findById(orgId).orElse(null);
            if (org != null) {
                com.example.taskflow.domain.OrganizationMembership m = membershipRepository.findByUserAndOrganization(caller, org).orElse(null);
                if (m != null && m.getOrgRole() != null && m.getOrgRole().getPriority() != null) {
                    return m.getOrgRole().getPriority();
                }
            }
            return 100; // Default lowest power
        } else {
            // Global roles
            return caller.getRoles().stream()
                    .map(r -> r.getPriority() != null ? r.getPriority() : 100)
                    .min(Integer::compareTo).orElse(100);
        }
    }

    private void requireOrgPermission(User caller, Long orgId, String permissionName) {
        boolean isSuperAdmin = caller.getRoles().stream()
                .anyMatch(r -> r.getName().endsWith("SUPER_ADMIN"));
        if (isSuperAdmin) return;

        if (orgId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Global role management requires SUPER_ADMIN.");
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        com.example.taskflow.domain.OrganizationMembership m = membershipRepository.findByUserAndOrganization(caller, org).orElse(null);
        
        boolean hasPerm = m != null && m.getOrgRole() != null && m.getOrgRole().getPermissions().stream()
                .anyMatch(p -> p.getName().equals(permissionName));

        if (!hasPerm) {
            throw new org.springframework.security.access.AccessDeniedException("You lack the '" + permissionName + "' permission in this organization.");
        }
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
        requireOrgPermission(caller, request.organizationId(), "ROLE_MANAGE");
        // RB-M06 fix: block reserved builtin role names. Previously an org admin
        // could create a custom role named "ADMIN" in their org, which would then
        // be indistinguishable from the builtin ADMIN role in name-based
        // isBuiltinAdmin() checks. The CORE_ROLES set already exists for update
        // guarding  -  we reuse it here for creation guarding.
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
        Integer reqPriority = request.priority() != null ? request.priority() : 100;
        Integer callerPriority = getCallerPriority(caller, request.organizationId());
        if (reqPriority < callerPriority) {
            throw new IllegalArgumentException("You cannot create a role with a higher priority (lower number) than your own.");
        }
        role.setPriority(reqPriority);
        
        if (request.organizationId() != null) {
            Organization org = organizationRepository.findById(request.organizationId())
                    .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
            role.setOrganization(org);
        }
        
        Role saved = roleRepository.save(role);

        auditService.recordSync("ROLE_CREATED", caller, "ROLE", saved.getId(),
                null, mapToRoleResponseDTO(saved), "Created role: " + saved.getName());

        return mapToRoleResponseDTO(saved);
    }

    @Transactional
    public RoleResponseDTO updateRole(Long id, RoleUpdateRequestDTO request, User caller) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        Long orgId = role.getOrganization() != null ? role.getOrganization().getId() : null;
        requireOrgPermission(caller, orgId, "ROLE_MANAGE");
        
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
        
        Integer reqPriority = request.priority() != null ? request.priority() : 100;
        if (!reqPriority.equals(role.getPriority())) {
            Integer callerPriority = getCallerPriority(caller, orgId);
            if (reqPriority < callerPriority) {
                throw new IllegalArgumentException("You cannot update a role to have a higher priority (lower number) than your own.");
            }
            // Also ensure they aren't demoting a role that already outranks them
            if (role.getPriority() != null && role.getPriority() < callerPriority) {
                throw new IllegalArgumentException("You cannot update a role that outranks your own priority.");
            }
            role.setPriority(reqPriority);
        }
        
        Role saved = roleRepository.save(role);
        
        auditService.recordSync("ROLE_UPDATED", caller, "ROLE", saved.getId(),
                oldValue, mapToRoleResponseDTO(saved), "Updated role: " + saved.getName());
                
        return mapToRoleResponseDTO(saved);
    }

    @Transactional
    public void deleteRole(Long id, User caller) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        Long orgId = role.getOrganization() != null ? role.getOrganization().getId() : null;
        requireOrgPermission(caller, orgId, "ROLE_MANAGE");
        
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
        
        auditService.recordSync("ROLE_DELETED", caller, "ROLE", id,
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
        
        Long orgId = role.getOrganization() != null ? role.getOrganization().getId() : null;
        requireOrgPermission(caller, orgId, "ROLE_MANAGE");
        
        boolean callerIsSuperAdmin = caller.getRoles().stream()
            .anyMatch(r -> r.getName().endsWith("SUPER_ADMIN"));
            
        if (!callerIsSuperAdmin) {
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
            
        auditService.recordSync("ROLE_PERMISSIONS_CHANGED", caller, "ROLE", id,
                oldPerms.stream().map(Permission::getName).collect(Collectors.toList()),
                newPermsDTO.stream().map(PermissionResponseDTO::getName).collect(Collectors.toList()),
                "Updated permissions for role: " + role.getName());
        
        return newPermsDTO;
    }
}
