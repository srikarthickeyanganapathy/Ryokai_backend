package com.taskflow.config;

import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RoleRepository;
import com.example.taskflow.security.PermissionCode;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

@Configuration
public class DataInitializer {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public DataInitializer(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    @PostConstruct
    public void initPermissions() {
        PermissionCode[] codesToSeed = {
            PermissionCode.MEMBER_EXIT_APPROVE,
            PermissionCode.EXIT_REQUEST_CREATE,
            PermissionCode.EXIT_REQUEST_VIEW,
            PermissionCode.EXIT_REQUEST_APPROVE,
            PermissionCode.EXIT_REQUEST_REJECT
        };

        for (PermissionCode code : codesToSeed) {
            Optional<Permission> existingPerm = permissionRepository.findByCode(code.code());
            Permission perm;
            if (existingPerm.isEmpty()) {
                perm = new Permission();
                perm.setName(code.name());
                perm.setCode(code.name());
                perm.setModule(code.getModule().name());
                perm.setCategory(code.getCategory().name());
                perm.setDescription(code.getDescription());
                perm.setSystem(true);
                perm = permissionRepository.save(perm);
            } else {
                perm = existingPerm.get();
            }

            final Permission finalPerm = perm;
            String[] roles = {"ADMIN", "MANAGER"};
            for (String roleName : roles) {
                roleRepository.findByName(roleName).ifPresent(role -> {
                    boolean hasPermission = role.getRolePermissionScopes().stream()
                            .anyMatch(rps -> rps.getPermission().getId().equals(finalPerm.getId()));
                    if (!hasPermission) {
                        com.example.taskflow.organization.rbac.domain.RolePermissionScope rps = new com.example.taskflow.organization.rbac.domain.RolePermissionScope();
                        rps.setRole(role);
                        rps.setPermission(finalPerm);
                        role.getRolePermissionScopes().add(rps);
                        roleRepository.save(role);
                    }
                });
            }
        }
    }
}
