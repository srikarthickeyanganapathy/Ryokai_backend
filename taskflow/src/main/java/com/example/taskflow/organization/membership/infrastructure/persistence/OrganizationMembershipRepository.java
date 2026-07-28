package com.example.taskflow.organization.membership.infrastructure.persistence;

import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.core.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Scope;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {
    List<OrganizationMembership> findByUserId(Long userId);

    @Query("SELECT DISTINCT om FROM OrganizationMembership om " +
           "JOIN FETCH om.user " +
           "LEFT JOIN FETCH om.orgRole r " +
           "LEFT JOIN FETCH r.rolePermissionScopes rps " +
           "LEFT JOIN FETCH rps.permission " +
           "LEFT JOIN FETCH rps.scope " +
           "WHERE om.organization.id = :orgId")
    List<OrganizationMembership> findByOrganizationId(@Param("orgId") Long orgId);

    Optional<OrganizationMembership> findByUserAndOrganization(User user, Organization org);

    boolean existsByUserAndOrganization(User user, Organization org);

    long countByOrganizationId(Long orgId);

    Optional<OrganizationMembership> findByUserIdAndOrganizationId(Long userId, Long orgId);

    boolean existsByUserIdAndOrganizationId(Long userId, Long orgId);
}