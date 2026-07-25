package com.example.taskflow.service.platform;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.RoleResponseDTO;

import java.util.List;
import java.util.Set;

/**
 * Control Plane user governance service interface per ADR-009.
 */
public interface PlatformUserService {
    Set<RoleResponseDTO> getUserRoles(Long userId);
    Set<RoleResponseDTO> assignUserRoles(Long userId, List<String> roleNames, User caller);
}
