package com.example.taskflow.organization.rbac.application;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.organization.rbac.dto.PermissionResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleCreateRequestDTO;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleUpdateRequestDTO;
import java.util.List;
import java.util.Set;

/**
 * Organization-scoped role and permission management service interface per ADR-009.
 */
public interface OrganizationRoleService {
    List<RoleResponseDTO> getRolesByOrganizationId(Long organizationId);
    RoleResponseDTO createRole(RoleCreateRequestDTO request, User caller);
    RoleResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request, User caller);
    void deleteRole(Long roleId, User caller);
    Set<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO> assignRolePermissions(Long roleId, AssignPermissionsRequestDTO request, User caller);
}