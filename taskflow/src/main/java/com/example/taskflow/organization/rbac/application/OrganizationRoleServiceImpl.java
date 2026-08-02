package com.example.taskflow.organization.rbac.application;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.dto.AssignPermissionsRequestDTO;
import com.example.taskflow.organization.rbac.dto.PermissionResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleCreateRequestDTO;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.dto.RoleUpdateRequestDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class OrganizationRoleServiceImpl implements OrganizationRoleService {

    private final RoleService roleService;

    public OrganizationRoleServiceImpl(RoleService roleService) {
        this.roleService = roleService;
    }

    @Override
    public List<RoleResponseDTO> getRolesByOrganizationId(Long organizationId) {
        return roleService.getRolesByOrganizationId(organizationId);
    }

    @Override
    public RoleResponseDTO createRole(RoleCreateRequestDTO request, User caller) {
        return roleService.createRole(request, caller);
    }

    @Override
    public RoleResponseDTO updateRole(Long roleId, RoleUpdateRequestDTO request, User caller) {
        return roleService.updateRole(roleId, request, caller);
    }

    @Override
    public void deleteRole(Long roleId, User caller) {
        roleService.deleteRole(roleId, caller);
    }

    @Override
    public Set<com.example.taskflow.organization.rbac.dto.RolePermissionAssignmentDTO> assignRolePermissions(Long roleId, AssignPermissionsRequestDTO request, User caller) {
        return roleService.assignRolePermissions(roleId, request, caller);
    }
}
