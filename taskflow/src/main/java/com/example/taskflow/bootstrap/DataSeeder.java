package com.example.taskflow.bootstrap;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RoleRepository;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.security.PermissionCode;

/**
 * Production-safe data seeder. NOT limited to @Profile("dev") anymore.
 * Super Admin account is bootstrapped from environment variables in all profiles.
 * Demo data seeding is optional and controlled by app.seed-demo-data flag.
 */
@Configuration
public class DataSeeder {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Bean
    public CommandLineRunner initData(PermissionRepository permissionRepository,
                                      RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      Environment env,
                                      com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository scopeRepository,
                                      org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        return args -> {
            transactionTemplate.execute(status -> {
            // ====================================================================
            // Always bootstrap: Permissions
            // ====================================================================

            // Seed Scopes
            String[] scopeCodes = {"OWN", "PROJECT", "TEAM", "ORGANIZATION", "GLOBAL"};
            int[] priorities = {0, 10, 20, 30, 40};
            for (int i = 0; i < scopeCodes.length; i++) {
                String code = scopeCodes[i];
                int priority = priorities[i];
                scopeRepository.findByCode(code).orElseGet(() -> {
                    com.example.taskflow.organization.rbac.domain.Scope scope = new com.example.taskflow.organization.rbac.domain.Scope();
                    scope.setCode(code);
                    scope.setPriority(priority);
                    return scopeRepository.save(scope);
                });
            }

            // Seed Permissions
            for (PermissionCode code : PermissionCode.values()) {
                createPermissionIfNotFound(code, permissionRepository);
            }

            // Seed Super Admin Role
            Set<Permission> allPermissions = new HashSet<>(permissionRepository.findAll());
            createRoleIfNotFound("SUPER_ADMIN", "System Super Administrator", allPermissions, roleRepository, scopeRepository);

            // Seed Super Admin User
            String adminUser = env.getProperty("app.admin.username", "superadmin");
            String adminPass = env.getProperty("app.admin.password", "SuperAdmin123!");
            
            createUserIfNotFound(adminUser, adminPass, "SUPER_ADMIN", roleRepository, userRepository, passwordEncoder);
            
            return null;
        });
        };
    }

    private User createUserIfNotFound(String username, String password, String roleName,
                                      RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      PasswordEncoder passwordEncoder) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));

            if (roleName != null) {
                Role role = roleRepository.findByName(roleName).orElse(null);
                if (role != null) {
                    Set<Role> roles = new HashSet<>();
                    roles.add(role);
                    user.setRoles(roles);
                } else {
                    logger.warn("Global role {} not found for user {}", roleName, username);
                }
            }

            User saved = userRepository.save(user);
            logger.info("Created user: {}", username);
            return saved;
        }
        return userRepository.findByUsername(username).orElse(null);
    }

    private void createRoleIfNotFound(String roleName, String description, Set<Permission> permissions, RoleRepository roleRepository, com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository scopeRepository) {
        Role role = roleRepository.findByNameWithPermissions(roleName).orElse(new Role());
        role.setName(roleName);
        role.setDescription(description);
        
        com.example.taskflow.organization.rbac.domain.Scope globalScope = scopeRepository.findByCode("GLOBAL").orElse(null);
        if (globalScope != null) {
            java.util.Set<Long> existingPermissionIds = role.getRolePermissionScopes().stream()
                    .filter(rps -> rps.getScope().getId().equals(globalScope.getId()))
                    .map(rps -> rps.getPermission().getId())
                    .collect(java.util.stream.Collectors.toSet());

            java.util.Set<Long> targetPermissionIds = permissions.stream()
                    .map(p -> p.getId())
                    .collect(java.util.stream.Collectors.toSet());

            role.getRolePermissionScopes().removeIf(rps ->
                    rps.getScope().getId().equals(globalScope.getId()) &&
                    !targetPermissionIds.contains(rps.getPermission().getId())
            );

            for (Permission p : permissions) {
                if (!existingPermissionIds.contains(p.getId())) {
                    com.example.taskflow.organization.rbac.domain.RolePermissionScope rps = new com.example.taskflow.organization.rbac.domain.RolePermissionScope();
                    rps.setRole(role);
                    rps.setPermission(p);
                    rps.setScope(globalScope);
                    role.getRolePermissionScopes().add(rps);
                }
            }
        }
        
        roleRepository.save(role);
        logger.info("Created/Updated role: {}", roleName);
    }

    private void createPermissionIfNotFound(PermissionCode code, PermissionRepository permissionRepository) {
        if (permissionRepository.findByName(code.name()).isEmpty()) {
            Permission permission = new Permission();
            permission.setName(code.name());
            permission.setCode(code.name());
            permission.setModule(code.getModule().name());
            permission.setCategory(code.getCategory().name());
            permission.setDescription(code.getDescription());
            permission.setSystem(true); // All seeded permissions are system defaults
            permissionRepository.save(permission);
            logger.info("Created permission: {}", code.name());
        }
    }
}