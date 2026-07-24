package com.example.taskflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.taskflow.domain.User;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.dto.ChangePasswordRequestDTO;
import com.example.taskflow.dto.SessionDTO;
import com.example.taskflow.dto.UpdateProfileRequestDTO;
import com.example.taskflow.dto.UserResponseDTO;
import com.example.taskflow.service.UserProfileService;
import com.example.taskflow.service.UserService;

import com.example.taskflow.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping(value = "/api/v1/users", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final UserProfileService userProfileService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserProfileService userProfileService, UserService userService,
                          JwtUtil jwtUtil) {
        this.userProfileService = userProfileService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(UserResponseDTO.from(user));
    }

    /**
     * Fix #5: GET /api/users now returns only users within the caller's organization.
     * If the caller is not in any org, only returns themselves.
     * Super Admins get all users.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponseDTO>> getAllUsers(@AuthenticationPrincipal UserDetails userDetails) {
        User caller = userService.getCurrentUser(userDetails.getUsername());
        List<UserResponseDTO> users = userService.getVisibleUsers(caller).stream()
                .map(UserResponseDTO::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        UserResponseDTO updated = userProfileService.updateProfile(user, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(value = "/me/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponseDTO> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        UserResponseDTO updated = userProfileService.uploadAvatar(user, file);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        userProfileService.changePassword(user, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SessionDTO>> getSessions(@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        String authHeader = request.getHeader("Authorization");
        String currentTokenId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            currentTokenId = jwtUtil.extractTokenId(token);
        }
        
        return ResponseEntity.ok(userProfileService.getSessions(user, currentTokenId));
    }

    @DeleteMapping("/me/sessions/{tokenId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> revokeSession(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String tokenId) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        userProfileService.revokeSession(user, tokenId);
        return ResponseEntity.noContent().build();
    }
    
    // Notification endpoints deferred to Task 6 as per C5.
}
