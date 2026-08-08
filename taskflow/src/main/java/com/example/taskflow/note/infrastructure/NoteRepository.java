package com.example.taskflow.note.infrastructure;

import com.example.taskflow.note.domain.Note;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByUserIdOrderByIsPinnedDescUpdatedAtDesc(Long userId);

    @Query("SELECT n FROM Note n WHERE n.organization.id = :orgId ORDER BY n.isPinned DESC, n.updatedAt DESC")
    List<Note> findByOrganizationIdOrderByIsPinnedDescUpdatedAtDesc(@Param("orgId") Long orgId);

    @Query("SELECT n FROM Note n WHERE n.crew.id = :crewId ORDER BY n.isPinned DESC, n.updatedAt DESC")
    List<Note> findByCrewIdOrderByIsPinnedDescUpdatedAtDesc(@Param("crewId") Long crewId);
}
