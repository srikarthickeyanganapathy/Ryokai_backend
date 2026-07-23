package com.example.taskflow.controller;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TeamMessageCreateRequestDTO;
import com.example.taskflow.dto.TeamMessageResponseDTO;
import com.example.taskflow.service.TeamMessageService;
import com.example.taskflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamMessageController {

    private final TeamMessageService teamMessageService;
    private final UserService userService;

    public TeamMessageController(TeamMessageService teamMessageService, UserService userService) {
        this.teamMessageService = teamMessageService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping("/{teamId}/messages")
    public ResponseEntity<List<TeamMessageResponseDTO>> getMessages(@PathVariable Long teamId,
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(teamMessageService.getMessages(teamId, getCurrentUser(userDetails)));
    }

    @PostMapping("/{teamId}/messages")
    public ResponseEntity<TeamMessageResponseDTO> sendMessage(@PathVariable Long teamId,
                                                              @Valid @RequestBody TeamMessageCreateRequestDTO request,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamMessageService.sendMessage(teamId, request, getCurrentUser(userDetails)));
    }

    @DeleteMapping("/{teamId}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long teamId,
                                              @PathVariable Long messageId,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        teamMessageService.deleteMessage(teamId, messageId, getCurrentUser(userDetails));
        return ResponseEntity.noContent().build();
    }
}
