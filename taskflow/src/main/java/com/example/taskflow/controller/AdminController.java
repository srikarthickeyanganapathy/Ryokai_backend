package com.example.taskflow.controller;

import com.example.taskflow.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.taskflow.dto.OrganizationResponseDTO;

import java.util.List;

/**
 * Super Admin platform management endpoints (Rule 8).
 * All endpoints require SUPER_ADMIN role.
 */
@RestController
@RequestMapping(value = "/api/v1/admin", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final OrganizationService organizationService;

    public AdminController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * List all organizations on the platform.
     */
    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationResponseDTO>> listAllOrganizations() {
        return ResponseEntity.ok(organizationService.listAllOrganizations());
    }

    /**
     * Get details of any organization.
     */
    @GetMapping("/organizations/{id}")
    public ResponseEntity<OrganizationResponseDTO> getOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.getOrganizationAsAdmin(id));
    }

    /**
     * Suspend an organization  -  prevents all members from performing org operations.
     */
    @PostMapping("/organizations/{id}/suspend")
    public ResponseEntity<OrganizationResponseDTO> suspendOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.suspendOrganization(id));
    }

    /**
     * Reactivate a suspended organization.
     */
    @PostMapping("/organizations/{id}/activate")
    public ResponseEntity<OrganizationResponseDTO> activateOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.activateOrganization(id));
    }

    /**
     * Soft-delete an organization.
     */
    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}
