package com.example.taskflow.controller;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.dto.OrganizationResponseDTO;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * Super Admin platform management endpoints (Rule 8).
 * All endpoints require SUPER_ADMIN role.
 */
@RestController
@RequestMapping(value = "/api/admin", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public AdminController(OrganizationRepository organizationRepository,
                           OrganizationMembershipRepository membershipRepository,
                           UserService userService) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * List all organizations on the platform.
     */
    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationResponseDTO>> listAllOrganizations() {
        List<OrganizationResponseDTO> orgs = organizationRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .toList();
        return ResponseEntity.ok(orgs);
    }

    /**
     * Get details of any organization.
     */
    @GetMapping("/organizations/{id}")
    public ResponseEntity<OrganizationResponseDTO> getOrganization(@PathVariable Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));
        return ResponseEntity.ok(mapToResponseDTO(org));
    }

    /**
     * Suspend an organization — prevents all members from performing org operations.
     */
    @PostMapping("/organizations/{id}/suspend")
    public ResponseEntity<OrganizationResponseDTO> suspendOrganization(@PathVariable Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (org.getStatus() == Organization.OrgStatus.DELETED) {
            throw new IllegalStateException("Cannot suspend a deleted organization");
        }

        org.setStatus(Organization.OrgStatus.SUSPENDED);
        Organization saved = organizationRepository.save(org);
        return ResponseEntity.ok(mapToResponseDTO(saved));
    }

    /**
     * Reactivate a suspended organization.
     */
    @PostMapping("/organizations/{id}/activate")
    public ResponseEntity<OrganizationResponseDTO> activateOrganization(@PathVariable Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (org.getStatus() == Organization.OrgStatus.DELETED) {
            throw new IllegalStateException("Cannot activate a deleted organization");
        }

        org.setStatus(Organization.OrgStatus.ACTIVE);
        Organization saved = organizationRepository.save(org);
        return ResponseEntity.ok(mapToResponseDTO(saved));
    }

    /**
     * Soft-delete an organization.
     */
    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        org.setStatus(Organization.OrgStatus.DELETED);
        organizationRepository.save(org);
        return ResponseEntity.noContent().build();
    }

    private OrganizationResponseDTO mapToResponseDTO(Organization org) {
        int memberCount = membershipRepository.findByOrganizationId(org.getId()).size();
        return new OrganizationResponseDTO(
                org.getId(),
                org.getName(),
                org.getDescription(),
                org.getCreatedBy() != null ? org.getCreatedBy().getUsername() : null,
                org.getCreatedAt(),
                memberCount
        );
    }
}
