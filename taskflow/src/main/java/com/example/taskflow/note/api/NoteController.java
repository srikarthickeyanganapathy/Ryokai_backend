package com.example.taskflow.note.api;

import com.example.taskflow.note.application.NoteService;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.example.taskflow.note.api.NoteDTOs.NoteRequestDTO;
import com.example.taskflow.note.api.NoteDTOs.NoteResponseDTO;
import com.example.taskflow.user.application.UserService;
import com.example.taskflow.user.domain.User;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/notes", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final UserService userService;

    /**
     * List notes for the current workspace scope.
     * No scope params = personal notes; ?orgId=1 = org notes; ?crewId=1 = crew notes.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NoteResponseDTO>> list(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(noteService.getNotesInScope(user, orgId, crewId));
    }

    /**
     * Create a note in the current workspace scope (query param wins over body scope).
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NoteResponseDTO> create(@RequestBody NoteRequestDTO req,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        Long effectiveOrg = orgId != null ? orgId : req.getOrgId();
        Long effectiveCrew = crewId != null ? crewId : req.getCrewId();
        return ResponseEntity.ok(noteService.create(user, req, effectiveOrg, effectiveCrew));
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
