package com.example.taskflow.organization.rbac.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignPermissionsRequestDTO(
    @NotNull(message = "Permissions list cannot be null")
    List<PermissionScopeAssignmentDTO> permissions
) {
    public record PermissionScopeAssignmentDTO(
        @NotNull(message = "Permission code/name cannot be null")
        String permissionName,
        
        @NotNull(message = "Scope code cannot be null")
        String scopeCode,

        List<ResourceAssignmentDTO> resourceAssignments
    ) {}

    public record ResourceAssignmentDTO(
        @NotNull(message = "Resource Type cannot be null")
        String resourceType,
        
        @NotNull(message = "Resource ID cannot be null")
        Long resourceId
    ) {}
}