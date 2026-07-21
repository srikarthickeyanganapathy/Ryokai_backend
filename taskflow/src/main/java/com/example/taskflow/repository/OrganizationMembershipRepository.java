package com.example.taskflow.repository;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {
    List<OrganizationMembership> findByUserId(Long userId);

    @Query("SELECT om FROM OrganizationMembership om JOIN FETCH om.user WHERE om.organization.id = :orgId")
    List<OrganizationMembership> findByOrganizationId(@Param("orgId") Long orgId);

    Optional<OrganizationMembership> findByUserAndOrganization(User user, Organization org);

    boolean existsByUserAndOrganization(User user, Organization org);

    long countByOrganizationId(Long orgId);
}
