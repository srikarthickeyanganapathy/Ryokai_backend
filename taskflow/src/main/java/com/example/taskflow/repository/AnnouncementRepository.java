package com.example.taskflow.repository;

import com.example.taskflow.domain.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Page<Announcement> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, Pageable pageable);
}
