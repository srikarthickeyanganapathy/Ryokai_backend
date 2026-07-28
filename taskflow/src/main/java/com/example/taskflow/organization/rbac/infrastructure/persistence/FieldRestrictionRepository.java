package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.organization.rbac.domain.FieldRestriction;

@Repository
public interface FieldRestrictionRepository extends JpaRepository<FieldRestriction, Long> {

    List<FieldRestriction> findByRoleIdAndResourceType(Long roleId, String resourceType);

    List<FieldRestriction> findByRoleIdInAndResourceType(List<Long> roleIds, String resourceType);
}
