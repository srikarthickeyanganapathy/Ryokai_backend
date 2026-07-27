package com.example.taskflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByName(String name);
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByOrderByNameAsc();
    List<Permission> findAllByOrderByModuleAscCodeAsc();
    List<Permission> findByModule(String module);
}
