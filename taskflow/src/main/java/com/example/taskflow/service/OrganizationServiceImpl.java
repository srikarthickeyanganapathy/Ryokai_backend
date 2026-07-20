package com.example.taskflow.service;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.OrganizationResponseDTO;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.RoleRepository;
import com.example.taskflow.repository.PermissionRepository;
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

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                               OrganizationMembershipRepository membershipRepository,
                               AuditService auditService,
                               RoleRepository roleRepository,
                               PermissionRepository permissionRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.auditService = auditService;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
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

        java.util.Set<com.example.taskflow.domain.Permission> adminPerms = loadPermissionsByName(
            "TASK_VIEW", "TASK_ASSIGN", "TASK_EDIT", "TASK_DELETE",
            "TASK_REVIEW", "TASK_DEPENDENCY_EDIT",
            "TASK_REASSIGN", "TASK_ARCHIVE", "ROLE_MANAGE",
            "ORG_MEMBER_INVITE", "ORG_MEMBER_REMOVE", "LEAVE_REQUEST_MANAGE",
            "TEAM_CREATE", "TEAM_MANAGE", "PROJECT_CREATE", "PROJECT_MANAGE",
            "TASK_OVERRIDE");

        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Organization Administrator");
        adminRole.setBuiltin(true);
        adminRole.setOrganization(saved);
        adminRole.setPermissions(adminPerms);
        adminRole.setPriority(0);
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
    @Transactional(readOnly = true)
    public OrganizationResponseDTO getUserOrganization(Long userId) {
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty())
            return null;
        return mapToResponseDTO(memberships.get(0).getOrganization());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO> listAllOrganizations() {
        return organizationRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponseDTO getOrganizationAsAdmin(Long orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        return mapToResponseDTO(org);
    }

    @Override
    @Transactional
    public OrganizationResponseDTO suspendOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (org.getStatus() == Organization.OrgStatus.DELETED) {
            throw new IllegalStateException("Cannot suspend a deleted organization");
        }

        org.setStatus(Organization.OrgStatus.SUSPENDED);
        return mapToResponseDTO(organizationRepository.save(org));
    }

    @Override
    @Transactional
    public OrganizationResponseDTO activateOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (org.getStatus() == Organization.OrgStatus.DELETED) {
            throw new IllegalStateException("Cannot activate a deleted organization");
        }

        org.setStatus(Organization.OrgStatus.ACTIVE);
        return mapToResponseDTO(organizationRepository.save(org));
    }

    @Override
    @Transactional
    public void deleteOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        org.setStatus(Organization.OrgStatus.DELETED);
        organizationRepository.save(org);
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

    private java.util.Set<com.example.taskflow.domain.Permission> loadPermissionsByName(String... names) {
        java.util.Set<com.example.taskflow.domain.Permission> perms = new java.util.HashSet<>();
        for (String name : names) {
            permissionRepository.findByName(name).ifPresent(perms::add);
        }
        return perms;
    }
}
