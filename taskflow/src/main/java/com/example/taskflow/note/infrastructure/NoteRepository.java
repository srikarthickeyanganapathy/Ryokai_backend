package com.example.taskflow.note.infrastructure;

import com.example.taskflow.note.domain.Note;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByUserIdOrderByIsPinnedDescUpdatedAtDesc(Long userId);
}
