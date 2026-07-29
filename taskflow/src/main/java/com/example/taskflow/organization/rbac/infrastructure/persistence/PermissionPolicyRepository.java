package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.taskflow.organization.rbac.domain.PermissionPolicyMapping;

public interface PermissionPolicyRepository extends JpaRepository<PermissionPolicyMapping, Long> {

    @Query("SELECT pp FROM PermissionPolicyMapping pp " +
           "WHERE pp.permission.code = :permissionCode " +
           "ORDER BY pp.evaluationOrder ASC")
    List<PermissionPolicyMapping> findByPermissionCodeOrdered(
            @Param("permissionCode") String permissionCode);

    List<PermissionPolicyMapping> findByPermissionId(Long permissionId);
}