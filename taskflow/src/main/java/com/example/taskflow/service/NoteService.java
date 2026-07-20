package com.example.taskflow.service;

import com.example.taskflow.domain.Note;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.NoteDTOs.NoteRequestDTO;
import com.example.taskflow.dto.NoteDTOs.NoteResponseDTO;
import com.example.taskflow.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    public List<NoteResponseDTO> getNotes(User user) {
        return noteRepository.findByUserIdOrderByIsPinnedDescUpdatedAtDesc(user.getId())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public NoteResponseDTO create(User user, NoteRequestDTO req) {
        Note note = new Note();
        applyRequest(note, req);
        note.setUser(user);
        return toDto(noteRepository.save(note));
    }

    @Transactional
    public NoteResponseDTO update(User user, Long id, NoteRequestDTO req) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + id));
        assertOwner(user, note);
        applyRequest(note, req);
        return toDto(noteRepository.save(note));
    }

    @Transactional
    public void delete(User user, Long id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + id));
        assertOwner(user, note);
        noteRepository.delete(note);
    }

    private void assertOwner(User user, Note note) {
        if (!note.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Cannot modify another user's note");
        }
    }

    private void applyRequest(Note note, NoteRequestDTO req) {
        note.setTitle(req.getTitle());
        note.setContent(req.getContent());
        note.setIsPinned(Boolean.TRUE.equals(req.getIsPinned()));
        note.setColor(req.getColor());
    }

    private NoteResponseDTO toDto(Note n) {
        return new NoteResponseDTO(n.getId(), n.getTitle(), n.getContent(),
                n.getIsPinned(), n.getColor(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
