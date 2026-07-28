package com.example.taskflow.organization.rbac.application;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.organization.rbac.dto.PermissionResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleCreateRequestDTO;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleUpdateRequestDTO;
import java.util.List;
import java.util.Set;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;

/**
 * Organization-scoped role and permission management service interface per ADR-009.
 */
public interface OrganizationRoleService {
    List<RoleResponseDTO> getRolesByOrganizationId(Long organizationId);
    RoleResponseDTO createRole(RoleCreateRequestDTO request, User caller);
    RoleResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request, User caller);
    void deleteRole(Long roleId, User caller);
    Set<PermissionResponseDTO> assignRolePermissions(Long roleId, AssignPermissionsRequestDTO request, User caller);
}