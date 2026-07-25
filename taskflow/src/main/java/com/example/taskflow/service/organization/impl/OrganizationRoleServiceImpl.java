package com.example.taskflow.service.organization.impl;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.*;
import com.example.taskflow.service.RoleService;
import com.example.taskflow.service.organization.OrganizationRoleService;
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
    public Set<PermissionResponseDTO> assignRolePermissions(Long roleId, AssignPermissionsRequestDTO request, User caller) {
        return roleService.assignRolePermissions(roleId, request, caller);
    }
}
