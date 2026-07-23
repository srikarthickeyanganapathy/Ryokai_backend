package com.example.taskflow.controller;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.AnnouncementRequestDTO;
import com.example.taskflow.dto.AnnouncementResponseDTO;
import com.example.taskflow.service.AnnouncementService;
import com.example.taskflow.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/organizations/{orgId}/announcements", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@Validated
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;

    public AnnouncementController(AnnouncementService announcementService, UserService userService) {
        this.announcementService = announcementService;
        this.userService = userService;
    }

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AnnouncementResponseDTO>> listAnnouncements(
            @PathVariable @Min(1) Long orgId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        Page<AnnouncementResponseDTO> response = announcementService.listAnnouncements(orgId, user, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AnnouncementResponseDTO> createAnnouncement(
            @PathVariable @Min(1) Long orgId,
            @Valid @RequestBody AnnouncementRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        AnnouncementResponseDTO response = announcementService.createAnnouncement(orgId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAnnouncement(
            @PathVariable @Min(1) Long orgId,
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        announcementService.deleteAnnouncement(orgId, id, user);
        return ResponseEntity.noContent().build();
    }
}
