package com.example.taskflow.repository;

import com.example.taskflow.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findByCreatedById(Long userId);
    Optional<Organization> findByName(String name);
}
