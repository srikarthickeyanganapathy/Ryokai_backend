package com.example.taskflow.organization.rbac.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Scope;

public record AssignPermissionsRequestDTO(
    @NotNull(message = "Permissions list cannot be null")
    List<PermissionScopeAssignmentDTO> permissions
) {
    public record PermissionScopeAssignmentDTO(
        @NotNull(message = "Permission code/name cannot be null")
        String permissionName,
        
        @NotNull(message = "Scope code cannot be null")
        String scopeCode
    ) {}
}