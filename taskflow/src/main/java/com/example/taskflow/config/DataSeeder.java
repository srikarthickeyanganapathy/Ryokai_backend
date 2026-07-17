package com.example.taskflow.config;

import java.util.Arrays;
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

import com.example.taskflow.domain.Permission;
import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.PermissionRepository;
import com.example.taskflow.repository.RoleRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.security.PermissionType;

import jakarta.transaction.Transactional;

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
    @Transactional
    public CommandLineRunner initData(PermissionRepository permissionRepository) {
        return args -> {
            // ====================================================================
            // Always bootstrap: Permissions
            // ====================================================================

            // Seed Permissions
            for (PermissionType type : PermissionType.values()) {
                createPermissionIfNotFound(type.name(), type.getDescription(), permissionRepository);
            }
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

    private void createRoleIfNotFound(String roleName, String description, Set<Permission> permissions, RoleRepository roleRepository) {
        Role role = roleRepository.findByName(roleName).orElse(new Role());
        role.setName(roleName);
        role.setDescription(description);
        role.setPermissions(permissions);
        roleRepository.save(role);
        logger.info("Created/Updated role: {}", roleName);
    }

    private void createPermissionIfNotFound(String name, String description, PermissionRepository permissionRepository) {
        if (permissionRepository.findByName(name).isEmpty()) {
            Permission permission = new Permission();
            permission.setName(name);
            permission.setDescription(description);
            permissionRepository.save(permission);
            logger.info("Created permission: {}", name);
        }
    }

    private Set<Permission> getPermissions(PermissionRepository permissionRepository, String... permissionNames) {
        Set<Permission> permissions = new HashSet<>();
        for (String name : permissionNames) {
            permissionRepository.findByName(name).ifPresent(permissions::add);
        }
        return permissions;
    }
}
