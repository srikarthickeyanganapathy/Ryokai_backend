package com.example.taskflow.controller;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.NoteDTOs.NoteRequestDTO;
import com.example.taskflow.dto.NoteDTOs.NoteResponseDTO;
import com.example.taskflow.service.NoteService;
import com.example.taskflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/notes", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NoteResponseDTO>> list(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(noteService.getNotes(user));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NoteResponseDTO> create(@RequestBody NoteRequestDTO req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(noteService.create(user, req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NoteResponseDTO> update(@PathVariable Long id, @RequestBody NoteRequestDTO req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(noteService.update(user, id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        noteService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
