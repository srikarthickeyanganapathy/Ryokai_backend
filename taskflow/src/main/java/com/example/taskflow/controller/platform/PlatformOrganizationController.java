package com.example.taskflow.controller.platform;

import com.example.taskflow.service.platform.PlatformOrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.taskflow.dto.OrganizationResponseDTO;

import java.util.List;

/**
 * Super Admin / Platform Control Plane organization management endpoints.
 * All endpoints require SUPER_ADMIN role (Platform authorization).
 * Migrated from AdminController to dedicated platform package and namespace per ADR-009.
 */
@RestController
@RequestMapping(value = "/api/v1/platform", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformOrganizationController {

    private final PlatformOrganizationService platformOrganizationService;

    public PlatformOrganizationController(PlatformOrganizationService platformOrganizationService) {
        this.platformOrganizationService = platformOrganizationService;
    }

    /**
     * List all organizations on the platform.
     */
    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationResponseDTO>> listAllOrganizations() {
        return ResponseEntity.ok(platformOrganizationService.listAllOrganizations());
    }

    /**
     * Get details of any organization.
     */
    @GetMapping("/organizations/{id}")
    public ResponseEntity<OrganizationResponseDTO> getOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.getOrganizationAsAdmin(id));
    }

    /**
     * Suspend an organization - prevents all members from performing org operations.
     */
    @PostMapping("/organizations/{id}/suspend")
    public ResponseEntity<OrganizationResponseDTO> suspendOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.suspendOrganization(id));
    }

    /**
     * Reactivate a suspended organization.
     */
    @PostMapping("/organizations/{id}/activate")
    public ResponseEntity<OrganizationResponseDTO> activateOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.activateOrganization(id));
    }

    /**
     * Soft-delete an organization.
     */
    @DeleteMapping("/organizations/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        platformOrganizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}
