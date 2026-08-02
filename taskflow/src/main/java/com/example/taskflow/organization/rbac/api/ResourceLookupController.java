package com.example.taskflow.organization.rbac.api;

import com.example.taskflow.organization.rbac.application.ResourceLookupService;
import com.example.taskflow.organization.rbac.dto.ResourceLookupDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/lookup")
public class ResourceLookupController {

    private final ResourceLookupService resourceLookupService;

    public ResourceLookupController(ResourceLookupService resourceLookupService) {
        this.resourceLookupService = resourceLookupService;
    }

    @GetMapping("/{resourceType}")
    @PreAuthorize("hasPermission(#orgId, 'Organization', 'ORG_VIEW') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ResourceLookupDTO>> lookupResources(
            @PathVariable Long orgId,
            @PathVariable String resourceType) {
        return ResponseEntity.ok(resourceLookupService.lookupResources(orgId, resourceType));
    }
}
