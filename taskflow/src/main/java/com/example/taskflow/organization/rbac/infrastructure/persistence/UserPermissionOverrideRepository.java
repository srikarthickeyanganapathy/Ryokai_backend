package com.example.taskflow.organization.rbac.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.taskflow.organization.rbac.domain.UserPermissionOverride;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {

    /**
     * Find active (non-expired) overrides for a user in an organization.
     */
    @Query("SELECT upo FROM UserPermissionOverride upo " +
           "JOIN FETCH upo.permission p " +
           "JOIN FETCH upo.scope s " +
           "WHERE upo.user.id = :userId " +
           "AND upo.organization.id = :orgId " +
           "AND (upo.expiresAt IS NULL OR upo.expiresAt > :now)")
    List<UserPermissionOverride> findActiveByUserAndOrg(
            @Param("userId") Long userId,
            @Param("orgId") Long orgId,
            @Param("now") LocalDateTime now);

    /**
     * Find a specific override for a user, org, permission, and scope.
     */
    Optional<UserPermissionOverride> findByUserIdAndOrganizationIdAndPermissionIdAndScopeId(
            Long userId, Long organizationId, Long permissionId, Long scopeId);

    /**
     * Find all overrides for a user (for cache warming).
     */
    List<UserPermissionOverride> findByUserId(Long userId);
}