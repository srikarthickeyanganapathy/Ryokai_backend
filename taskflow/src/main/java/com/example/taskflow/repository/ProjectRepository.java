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

    @Query("SELECT p FROM Project p WHERE p.scope = com.example.taskflow.domain.ProjectScope.PERSONAL AND (p.createdBy.id = :userId OR p.ownerUser.id = :userId) AND p.deleted = false")
    List<Project> findPersonalProjects(@Param("userId") Long userId);

    @Query("SELECT p FROM Project p WHERE p.scope = com.example.taskflow.domain.ProjectScope.CREW AND p.crew.id = :crewId AND p.deleted = false")
    List<Project> findCrewProjects(@Param("crewId") Long crewId);

    @Query("SELECT p FROM Project p WHERE p.scope = com.example.taskflow.domain.ProjectScope.ORGANIZATION AND p.organization.id = :orgId AND p.deleted = false")
    List<Project> findOrganizationProjects(@Param("orgId") Long orgId);

    List<Project> findByCreatedById(Long createdById);

    @Query("SELECT p FROM Project p JOIN p.collaborators c WHERE c.id = :userId")
    List<Project> findByCollaboratorsId(@Param("userId") Long userId);

    @Query("SELECT p FROM Project p WHERE p.crew.id = :crewId")
    List<Project> findByCrewId(@Param("crewId") Long crewId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Project p " +
           "LEFT JOIN p.sharedCrews sc " +
           "LEFT JOIN CrewMember cm_shared ON cm_shared.crew.id = sc.id AND cm_shared.user.id = :userId " +
           "LEFT JOIN CrewMember cm_native ON cm_native.crew.id = p.crew.id AND cm_native.user.id = :userId " +
           "LEFT JOIN p.collaborators c " +
           "WHERE p.id = :projectId AND (cm_shared.id IS NOT NULL OR cm_native.id IS NOT NULL OR c.id = :userId OR p.createdBy.id = :userId)")
    boolean isProjectSharedWithUser(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
