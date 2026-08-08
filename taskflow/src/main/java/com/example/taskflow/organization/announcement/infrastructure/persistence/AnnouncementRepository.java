package com.example.taskflow.organization.announcement.infrastructure.persistence;

import com.example.taskflow.organization.announcement.domain.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    @EntityGraph(attributePaths = {"organization","author"})
    @Override
    java.util.Optional<Announcement> findById(Long id);

    Page<Announcement> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);
}
