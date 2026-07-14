package com.example.taskflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.Crew;

@Repository
public interface CrewRepository extends JpaRepository<Crew, Long> {

    Optional<Crew> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Crew> findByCreator_Id(Long creatorId);
}
