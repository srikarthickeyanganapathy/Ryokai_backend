package com.example.taskflow.organization.membership.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.organization.membership.domain.OrganizationInvite;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, Long> {
    List<OrganizationInvite> findByInviteeUserIdAndStatus(Long userId, OrganizationInvite.InviteStatus status);
    List<OrganizationInvite> findByOrganizationId(Long orgId);
    Optional<OrganizationInvite> findByInviteeUserIdAndOrganizationIdAndStatus(Long userId, Long orgId, OrganizationInvite.InviteStatus status);
    boolean existsByInviteeUserIdAndOrganizationIdAndStatus(Long userId, Long orgId, OrganizationInvite.InviteStatus status);
    Optional<OrganizationInvite> findByToken(String token);
}