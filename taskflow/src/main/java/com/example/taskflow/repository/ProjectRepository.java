package com.example.taskflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.Project;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOrganizationId(Long organizationId);

    List<Project> findByTeamId(Long teamId);

    @Query("SELECT p FROM Project p WHERE " +
           "(:orgId IS NULL OR p.organization.id = :orgId) AND " +
           "(:teamId IS NULL OR p.team.id = :teamId)")
    List<Project> findByOrganizationIdAndTeamId(
            @Param("orgId") Long organizationId,
            @Param("teamId") Long teamId);

    List<Project> findByCreatedById(Long createdById);
}
