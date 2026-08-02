package com.example.taskflow.organization.rbac.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;

import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.rbac.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.organization.rbac.dto.PermissionResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleCreateRequestDTO;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleUpdateRequestDTO;
import com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RoleRepository;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.user.domain.User;

import jakarta.transaction.Transactional;
import com.example.taskflow.audit.application.AuditService;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuthorizationEngine authorizationEngine;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository membershipRepository;
    private final AuditService auditService;
    private final com.example.taskflow.organization.rbac.infrastructure.persistence.RolePermissionScopeRepository rolePermissionScopeRepository;
    private final com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository scopeRepository;

    // Define core roles that cannot be renamed
    private static final Set<String> CORE_ROLES = Set.of("SUPER_ADMIN", "ADMIN");

    public RoleService(RoleRepository roleRepository, 
                       PermissionRepository permissionRepository,
                       AuthorizationEngine authorizationEngine,
                       UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository membershipRepository,
                       AuditService auditService,
                       com.example.taskflow.organization.rbac.infrastructure.persistence.RolePermissionScopeRepository rolePermissionScopeRepository,
                       com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository scopeRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.authorizationEngine = authorizationEngine;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.auditService = auditService;
        this.rolePermissionScopeRepository = rolePermissionScopeRepository;
        this.scopeRepository = scopeRepository;
    }

    private PermissionResponseDTO mapToPermissionResponseDTO(Permission p) {
        return mapToPermissionResponseDTO(p, "ORGANIZATION");
    }

    private PermissionResponseDTO mapToPermissionResponseDTO(Permission p, String scopeCode) {
        return new PermissionResponseDTO(
            p.getId(), 
            p.getName(), 
            p.getCode(), 
            p.getModule(), 
            p.getCategory(), 
            p.getDescription(), 
            p.isSystem(),
            scopeCode
        );
    }

    private com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO mapToRolePermissionAssignmentDTO(com.example.taskflow.organization.rbac.domain.RolePermissionScope rps) {
        List<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO.ResourceAssignmentDTO> raDTOs = new java.util.ArrayList<>();
        if (rps.getResourceAssignments() != null) {
            for (com.example.taskflow.organization.rbac.domain.ResourceAssignment ra : rps.getResourceAssignments()) {
                raDTOs.add(new com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO.ResourceAssignmentDTO(
                    ra.getResourceType(), ra.getResourceId(), ra.getResourceType() + " " + ra.getResourceId() // fallback displayName until we fetch it
                ));
            }
        }
        return new com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO(
            rps.getPermission().getCode(),
            rps.getScope() != null ? rps.getScope().getCode() : "ORGANIZATION",
            raDTOs
        );
    }

    public RoleResponseDTO mapToRoleResponseDTO(Role r) {
        Set<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO> perms = r.getRolePermissionScopes() != null 
            ? r.getRolePermissionScopes().stream()
                .map(this::mapToRolePermissionAssignmentDTO)
                .collect(Collectors.toSet())
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
                com.example.taskflow.organization.membership.domain.OrganizationMembership m = membershipRepository.findByUserAndOrganization(caller, org).orElse(null);
                if (m != null && m.getOrgRole() != null && m.getOrgRole().getPriority() != null) {
                    return m.getOrgRole().getPriority();
                }
            }
            return 100; // Default lowest power
        } else {
            // Global roles
            return caller.getRoles().stream()
                    .map(r -> r.getPriority() != null ? r.getPriority() : 100)
                    .min((a, b) -> Integer.compare(a, b)).orElse(100);
        }
    }

    private void requireOrgPermission(User caller, Long orgId, String permissionName) {
        boolean isSuperAdmin = caller.isSuperAdmin();
        if (isSuperAdmin) return;

        if (orgId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Global role management requires SUPER_ADMIN.");
        }

        com.example.taskflow.security.PermissionCode pCode = com.example.taskflow.security.PermissionCode.valueOf(permissionName);
        if (pCode != null) {
            if (!authorizationEngine.authorize(com.example.taskflow.security.authorization.AuthorizationRequest.builder(caller, pCode).context(java.util.Map.of("organizationId", orgId)).requiredScope(com.example.taskflow.security.ScopeType.ORGANIZATION).build()).isGranted()) {
                throw new org.springframework.security.access.AccessDeniedException("You lack the '" + permissionName + "' permission in this organization.");
            }
        } else {
            throw new org.springframework.security.access.AccessDeniedException("Invalid permission code.");
        }
    }

    @Transactional
    public List<RoleResponseDTO> getAllRoles() {
        return roleRepository.findByOrganizationIdIsNullOrderByNameAsc().stream()
            .map(this::mapToRoleResponseDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<RoleResponseDTO> getRolesByOrganizationId(Long organizationId) {
        return roleRepository.findByOrganizationId(organizationId).stream()
            .map(this::mapToRoleResponseDTO)
            .collect(Collectors.toList());
    }

    @Transactional
    public RoleResponseDTO createRole(RoleCreateRequestDTO request, User caller) {
        requireOrgPermission(caller, request.organizationId(), "ROLE_CREATE");
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
        if (reqPriority == 0) {
            throw new IllegalArgumentException("Priority 0 is reserved for the built-in ADMIN role.");
        }
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
        requireOrgPermission(caller, orgId, "ROLE_UPDATE");
        
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
            if (reqPriority == 0) {
                throw new IllegalArgumentException("Priority 0 is reserved for the built-in ADMIN role.");
            }
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
        requireOrgPermission(caller, orgId, "ROLE_DELETE");
        
        if (CORE_ROLES.contains(role.getName())) {
            throw new IllegalArgumentException("Cannot delete built-in system role: " + role.getName());
        }
        
        if (userRepository.existsByRolesId(role.getId())) {
            throw new IllegalStateException("Cannot delete a role that is still assigned to users");
        }

        RoleResponseDTO oldValue = mapToRoleResponseDTO(role);
        roleRepository.delete(role);
        
        auditService.recordSync("ROLE_DELETED", caller, "ROLE", id,
                oldValue, null, "Deleted role: " + role.getName());
    }

    public List<PermissionResponseDTO> getAllPermissions() {
        return permissionRepository.findAllByOrderByNameAsc().stream()
            .map(this::mapToPermissionResponseDTO)
            .collect(Collectors.toList());
    }

    public Set<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO> getRolePermissions(Long id) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        return role.getRolePermissionScopes().stream()
            .map(this::mapToRolePermissionAssignmentDTO).collect(Collectors.toSet());
    }

    @Transactional
    public Set<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO> assignRolePermissions(Long id, AssignPermissionsRequestDTO request, User caller) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
        
        Long orgId = role.getOrganization() != null ? role.getOrganization().getId() : null;
        requireOrgPermission(caller, orgId, "ROLE_UPDATE");
        
        boolean callerIsSuperAdmin = caller.isSuperAdmin();
            
        if (!callerIsSuperAdmin) {
            boolean isOrgScopedRole = role.getOrganization() != null;
            
            if (!isOrgScopedRole) {
                // Only super admin can modify global roles
                throw new org.springframework.security.access.AccessDeniedException("Only SUPER_ADMIN may modify global roles");
            }
        }
        
        Set<Permission> oldPerms = role.getRolePermissionScopes().stream()
                .map(rps -> rps.getPermission())
                .collect(Collectors.toSet());
                
        role.getRolePermissionScopes().clear();
        rolePermissionScopeRepository.deleteByRoleId(id);
        rolePermissionScopeRepository.flush();
        roleRepository.saveAndFlush(role);

        for (var pAssign : request.permissions()) {
            Permission permission = permissionRepository.findByName(pAssign.permissionName())
                    .or(() -> permissionRepository.findByCode(pAssign.permissionName()))
                    .orElseThrow(() -> new RuntimeException("Permission not found: " + pAssign.permissionName()));

            List<String> supported = com.example.taskflow.security.PermissionMetadataRegistry.getSupportedScopes(permission.getCode());
            if (!supported.contains(pAssign.scopeCode())) {
                throw new IllegalArgumentException("Scope '" + pAssign.scopeCode() + "' is not supported for permission: " + permission.getCode());
            }

            com.example.taskflow.organization.rbac.domain.Scope scope = scopeRepository.findByCode(pAssign.scopeCode())
                    .orElseThrow(() -> new RuntimeException("Scope not found: " + pAssign.scopeCode()));
                    
            com.example.taskflow.organization.rbac.domain.RolePermissionScope rps = new com.example.taskflow.organization.rbac.domain.RolePermissionScope();
            rps.setRole(role);
            rps.setPermission(permission);
            rps.setScope(scope);
            
            if (pAssign.resourceAssignments() != null && !pAssign.resourceAssignments().isEmpty()) {
                if (rps.getResourceAssignments() == null) {
                    rps.setResourceAssignments(new java.util.ArrayList<>());
                }
                for (var raDTO : pAssign.resourceAssignments()) {
                    com.example.taskflow.organization.rbac.domain.ResourceAssignment ra = new com.example.taskflow.organization.rbac.domain.ResourceAssignment();
                    ra.setRolePermissionScope(rps);
                    ra.setResourceId(raDTO.resourceId());
                    ra.setResourceType(raDTO.resourceType());
                    rps.getResourceAssignments().add(ra);
                }
            }
            
            role.getRolePermissionScopes().add(rps);
        }
        
        roleRepository.save(role);

        
        Set<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO> newPermsDTO = role.getRolePermissionScopes().stream()
            .map(this::mapToRolePermissionAssignmentDTO).collect(Collectors.toSet());
            
        auditService.recordSync("ROLE_PERMISSIONS_CHANGED", caller, "ROLE", id,
                oldPerms.stream().map(p -> p.getName()).collect(Collectors.toList()),
                newPermsDTO.stream().map(p -> p.permissionCode()).collect(Collectors.toList()),
                "Updated permissions for role: " + role.getName());
        
        return newPermsDTO;
    }

    public Set<RoleResponseDTO> getUserRoles(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getRoles().stream()
                .map(this::mapToRoleResponseDTO)
                .collect(Collectors.toSet());
    }

    @Transactional
    public Set<RoleResponseDTO> assignUserRoles(Long userId, List<String> roleNames, User caller) {
        boolean callerIsSuperAdmin = caller.isSuperAdmin();

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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Set<String> oldRoles = user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet());
        
        Set<Role> newRoles = roleNames.stream()
                .map(requested -> {
                    String normalized = requested.replaceFirst("^ROLE_", "");
                    return roleRepository.findByName(normalized)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + normalized));
                })
                .collect(Collectors.toSet());
        user.setRoles(newRoles);
        userRepository.save(user);
        
        
        auditService.recordSync("USER_ROLES_ASSIGNED", caller, "USER", user.getId(),
                oldRoles, newRoles.stream().map(r -> r.getName()).collect(Collectors.toSet()),
                "Assigned roles to user " + user.getUsername());

        return newRoles.stream()
                .map(this::mapToRoleResponseDTO)
                .collect(Collectors.toSet());
    }
}