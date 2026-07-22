package com.example.taskflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.Crew;

import jakarta.persistence.LockModeType;

@Repository
public interface CrewRepository extends JpaRepository<Crew, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Crew c WHERE c.id = :id")
    Optional<Crew> findByIdWithLock(@Param("id") Long id);

    Optional<Crew> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Crew> findByCreator_Id(Long creatorId);

    List<Crew> findAllByOrderByCreatedAtDesc();
}
