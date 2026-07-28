package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Scope;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
    
    Optional<Role> findByNameAndOrganizationIdIsNull(String name);
    
    Optional<Role> findByNameAndOrganizationId(String name, Long organizationId);
    
    @Query("SELECT DISTINCT r FROM Role r " +
           "LEFT JOIN FETCH r.rolePermissionScopes rps " +
           "LEFT JOIN FETCH rps.permission " +
           "LEFT JOIN FETCH rps.scope " +
           "WHERE r.organization.id = :organizationId")
    List<Role> findByOrganizationId(@Param("organizationId") Long organizationId);
    
    List<Role> findByOrganizationIdIsNullOrderByNameAsc();
    
    List<Role> findAllByOrderByNameAsc();

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.rolePermissionScopes rps LEFT JOIN FETCH rps.permission LEFT JOIN FETCH rps.scope WHERE r.name = :name")
    Optional<Role> findByNameWithPermissions(String name);
}