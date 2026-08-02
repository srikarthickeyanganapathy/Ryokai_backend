package com.example.taskflow.organization.rbac.dto;

import java.util.List;

public record RolePermissionAssignmentDTO(
    String permissionCode,
    String scopeCode,
    List<ResourceAssignmentDTO> resourceAssignments
) {
    public record ResourceAssignmentDTO(
        String resourceType,
        Long resourceId,
        String displayName
    ) {}
}
