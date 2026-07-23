package com.example.taskflow.controller;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.WhiteboardDTOs.WhiteboardRequestDTO;
import com.example.taskflow.dto.WhiteboardDTOs.WhiteboardResponseDTO;
import com.example.taskflow.service.UserService;
import com.example.taskflow.service.WhiteboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/crews/{crewId}/whiteboards", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class WhiteboardController {

    private final WhiteboardService whiteboardService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WhiteboardResponseDTO>> list(@PathVariable Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(whiteboardService.list(user, crewId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WhiteboardResponseDTO> create(@PathVariable Long crewId,
            @RequestBody WhiteboardRequestDTO req, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(whiteboardService.create(user, crewId, req));
    }

    @PutMapping("/{boardId}/snapshot")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WhiteboardResponseDTO> saveSnapshot(@PathVariable Long crewId, @PathVariable Long boardId,
            @RequestBody Map<String, String> body, @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(whiteboardService.saveSnapshot(user, crewId, boardId, body.get("dataUrl")));
    }

    @DeleteMapping("/{boardId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable Long crewId, @PathVariable Long boardId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        whiteboardService.delete(user, crewId, boardId);
        return ResponseEntity.noContent().build();
    }
}
