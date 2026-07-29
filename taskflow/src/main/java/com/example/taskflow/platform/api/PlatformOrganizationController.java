package com.example.taskflow.platform.api;

import com.example.taskflow.platform.application.PlatformOrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.taskflow.organization.core.dto.OrganizationResponseDTO;

import java.util.List;

import com.example.taskflow.security.platform.PlatformAuthorize;
import com.example.taskflow.security.platform.PlatformPermission;

/**
 * Super Admin / Platform Control Plane organization management endpoints.
 * All endpoints require Platform authorization.
 * Migrated from AdminController to dedicated platform package and namespace per ADR-009.
 */
@RestController
@RequestMapping(value = "/api/v1/platform", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class PlatformOrganizationController {

    private final PlatformOrganizationService platformOrganizationService;

    public PlatformOrganizationController(PlatformOrganizationService platformOrganizationService) {
        this.platformOrganizationService = platformOrganizationService;
    }

    /**
     * List all organizations on the platform.
     */
    @GetMapping("/organizations")
    @PlatformAuthorize(PlatformPermission.ORG_VIEW_ALL)
    public ResponseEntity<List<OrganizationResponseDTO>> listAllOrganizations() {
        return ResponseEntity.ok(platformOrganizationService.listAllOrganizations());
    }

    /**
     * Get details of any organization.
     */
    @GetMapping("/organizations/{id}")
    @PlatformAuthorize(PlatformPermission.ORG_VIEW_DETAILS)
    public ResponseEntity<OrganizationResponseDTO> getOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.getOrganizationAsAdmin(id));
    }

    /**
     * Suspend an organization - prevents all members from performing org operations.
     */
    @PostMapping("/organizations/{id}/suspend")
    @PlatformAuthorize(PlatformPermission.ORG_SUSPEND)
    public ResponseEntity<OrganizationResponseDTO> suspendOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.suspendOrganization(id));
    }

    /**
     * Reactivate a suspended organization.
     */
    @PostMapping("/organizations/{id}/activate")
    @PlatformAuthorize(PlatformPermission.ORG_UNSUSPEND)
    public ResponseEntity<OrganizationResponseDTO> activateOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.activateOrganization(id));
    }

    /**
     * Soft-delete an organization.
     */
    @DeleteMapping("/organizations/{id}")
    @PlatformAuthorize(PlatformPermission.ORG_DELETE)
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        platformOrganizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}