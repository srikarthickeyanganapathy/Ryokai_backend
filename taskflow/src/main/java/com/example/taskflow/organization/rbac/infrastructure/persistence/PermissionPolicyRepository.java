package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.organization.rbac.domain.PermissionPolicyMapping;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.security.PermissionCode;

@Repository
public interface PermissionPolicyRepository extends JpaRepository<PermissionPolicyMapping, Long> {

    @Query("SELECT pp FROM PermissionPolicyMapping pp " +
           "WHERE pp.permission.code = :permissionCode " +
           "ORDER BY pp.evaluationOrder ASC")
    List<PermissionPolicyMapping> findByPermissionCodeOrdered(
            @Param("permissionCode") String permissionCode);

    List<PermissionPolicyMapping> findByPermissionId(Long permissionId);
}