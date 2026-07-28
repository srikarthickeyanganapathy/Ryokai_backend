package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.organization.rbac.domain.ResourceAssignment;

@Repository
public interface ResourceAssignmentRepository extends JpaRepository<ResourceAssignment, Long> {

    List<ResourceAssignment> findByRolePermissionScopeId(Long rolePermissionScopeId);

    List<ResourceAssignment> findByRolePermissionScopeIdIn(List<Long> rpsIds);

    void deleteByRolePermissionScopeId(Long rolePermissionScopeId);
}
