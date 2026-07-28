package com.example.taskflow.note;

import com.example.taskflow.note.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByUserIdOrderByIsPinnedDescUpdatedAtDesc(Long userId);
}
