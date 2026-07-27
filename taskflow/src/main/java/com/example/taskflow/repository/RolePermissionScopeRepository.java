package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.RolePermissionScope;

@Repository
public interface RolePermissionScopeRepository extends JpaRepository<RolePermissionScope, Long> {

    /**
     * Find all permission-scope grants for a specific role.
     */
    List<RolePermissionScope> findByRoleId(Long roleId);

    /**
     * Find all permission-scope grants for a role and specific permission code.
     */
    @Query("SELECT rps FROM RolePermissionScope rps " +
           "JOIN rps.permission p " +
           "WHERE rps.role.id = :roleId AND p.code = :permissionCode")
    List<RolePermissionScope> findByRoleIdAndPermissionCode(
            @Param("roleId") Long roleId,
            @Param("permissionCode") String permissionCode);

    /**
     * Find all grants for multiple roles at once (batch query for pipeline efficiency).
     */
    @Query("SELECT rps FROM RolePermissionScope rps " +
           "JOIN FETCH rps.permission p " +
           "JOIN FETCH rps.scope s " +
           "WHERE rps.role.id IN :roleIds")
    List<RolePermissionScope> findByRoleIdIn(@Param("roleIds") List<Long> roleIds);

    /**
     * Find grants for multiple roles and a specific permission code.
     */
    @Query("SELECT rps FROM RolePermissionScope rps " +
           "JOIN FETCH rps.permission p " +
           "JOIN FETCH rps.scope s " +
           "WHERE rps.role.id IN :roleIds AND p.code = :permissionCode")
    List<RolePermissionScope> findByRoleIdInAndPermissionCode(
            @Param("roleIds") List<Long> roleIds,
            @Param("permissionCode") String permissionCode);

    void deleteByRoleId(Long roleId);
}
