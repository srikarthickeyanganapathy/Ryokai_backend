package com.example.taskflow.organization.core.application;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.core.dto.OrganizationResponseDTO;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RoleRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionRepository;
import com.example.taskflow.audit.application.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final AuditService auditService;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository scopeRepository;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository membershipRepository,
                               AuditService auditService,
                               RoleRepository roleRepository,
                               PermissionRepository permissionRepository,
                               com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository scopeRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.auditService = auditService;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.scopeRepository = scopeRepository;
    }

    @Override
    @Transactional
    public OrganizationResponseDTO createOrganization(String name, String description, User adminUser) {
        if (!membershipRepository.findByUserId(adminUser.getId()).isEmpty()) {
            throw new IllegalStateException(
                    "You are already a member of an organization. You must leave your current organization before creating a new one.");
        }

        Organization org = new Organization();
        org.setName(name);
        org.setDescription(description);
        org.setCreatedBy(adminUser);
        Organization saved = organizationRepository.save(org);

        java.util.Set<com.example.taskflow.organization.rbac.domain.Permission> adminPerms = new java.util.HashSet<>(permissionRepository.findAll());

        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Organization Administrator");
        adminRole.setBuiltin(true);
        adminRole.setOrganization(saved);
        adminRole.setPriority(0);
        
        com.example.taskflow.organization.rbac.domain.Scope orgScope = scopeRepository.findByCode("ORGANIZATION").orElseThrow();
        adminRole.setMaxScope(orgScope);
        for (com.example.taskflow.organization.rbac.domain.Permission p : adminPerms) {
            com.example.taskflow.organization.rbac.domain.RolePermissionScope rps = new com.example.taskflow.organization.rbac.domain.RolePermissionScope();
            rps.setRole(adminRole);
            rps.setPermission(p);
            rps.setScope(orgScope);
            adminRole.getRolePermissionScopes().add(rps);
        }
        
        roleRepository.save(adminRole);

        OrganizationMembership membership = new OrganizationMembership();
        membership.setUser(adminUser);
        membership.setOrganization(saved);
        membership.setOrgRole(adminRole);
        membershipRepository.save(membership);

        OrganizationResponseDTO responseDTO = mapToResponseDTO(saved);
        auditService.record("ORG_CREATED", adminUser, "ORGANIZATION", saved.getId(),
                null, responseDTO, "Created organization: " + saved.getName());

        return responseDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponseDTO getOrganization(Long orgId, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        
        if (!caller.isSuperAdmin() && !caller.isMemberOf(org)) {
            throw new UnauthorizedActionException("You are not a member of this organization");
        }
        
        return mapToResponseDTO(org);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO> listUserOrganizations(Long userId) {
        return membershipRepository.findByUserId(userId).stream()
                .map(m -> mapToResponseDTO(m.getOrganization()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrganizationResponseDTO updateOrganization(Long orgId, String name, String description, User caller) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        
        OrganizationResponseDTO oldValue = mapToResponseDTO(org);
        
        if (name != null) {
            org.setName(name);
        }
        if (description != null) {
            org.setDescription(description);
        }
        
        Organization saved = organizationRepository.save(org);
        
        OrganizationResponseDTO responseDTO = mapToResponseDTO(saved);
        auditService.recordSync("ORG_UPDATED", caller, "ORGANIZATION", saved.getId(),
                oldValue, responseDTO, "Updated organization: " + saved.getName());
                
        return responseDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponseDTO getUserOrganization(Long userId) {
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty())
            return null;
        return mapToResponseDTO(memberships.get(0).getOrganization());
    }

    private OrganizationResponseDTO mapToResponseDTO(Organization org) {
        int memberCount = (int) membershipRepository.countByOrganizationId(org.getId());
        return new OrganizationResponseDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getDescription(),
                org.getCreatedBy() != null ? org.getCreatedBy().getUsername() : null,
                org.getCreatedAt(),
                memberCount);
    }
}