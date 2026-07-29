package com.example.taskflow.platform.application;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.application.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class PlatformUserServiceImpl implements PlatformUserService {

    private final RoleService roleService;

    public PlatformUserServiceImpl(RoleService roleService) {
        this.roleService = roleService;
    }

    @Override
    public Set<RoleResponseDTO> getUserRoles(Long userId) {
        return roleService.getUserRoles(userId);
    }

    @Override
    public Set<RoleResponseDTO> assignUserRoles(Long userId, List<String> roleNames, User caller) {
        return roleService.assignUserRoles(userId, roleNames, caller);
    }
}
