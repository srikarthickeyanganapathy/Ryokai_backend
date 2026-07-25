package com.example.taskflow.service.platform.impl;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.RoleResponseDTO;
import com.example.taskflow.service.RoleService;
import com.example.taskflow.service.platform.PlatformUserService;
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
