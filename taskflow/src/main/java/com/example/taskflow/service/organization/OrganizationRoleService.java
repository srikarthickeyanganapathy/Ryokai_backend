package com.example.taskflow.service.organization;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.*;
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
    Set<PermissionResponseDTO> assignRolePermissions(Long roleId, AssignPermissionsRequestDTO request, User caller);
}
