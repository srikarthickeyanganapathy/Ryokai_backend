package com.example.taskflow.organization.core.infrastructure.persistence;

import com.example.taskflow.organization.core.domain.Organization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @EntityGraph(attributePaths = {"createdBy"})
    @Override
    java.util.Optional<Organization> findById(Long id);

    List<Organization> findByCreatedById(Long userId);
    Optional<Organization> findByName(String name);
}
