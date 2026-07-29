package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.organization.rbac.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByOrderByNameAsc();
    List<Permission> findAllByOrderByModuleAscCodeAsc();
    List<Permission> findByModule(String module);
}
