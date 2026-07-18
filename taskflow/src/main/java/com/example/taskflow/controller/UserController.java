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
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping(value = "/api/users", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final UserProfileService userProfileService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final OrganizationMembershipRepository membershipRepository;

    public UserController(UserProfileService userProfileService, UserService userService,
                          JwtUtil jwtUtil,
                          OrganizationMembershipRepository membershipRepository) {
        this.userProfileService = userProfileService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.membershipRepository = membershipRepository;
    }

    @GetMapping("/me")
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

        // Super Admin can see all users
        boolean isSuperAdmin = caller.getRoles() != null && caller.getRoles().stream()
                .anyMatch(r -> {
                    String name = r.getName();
                    if (name.startsWith("ROLE_")) name = name.substring(5);
                    return "SUPER_ADMIN".equals(name);
                });

        if (isSuperAdmin) {
            return ResponseEntity.ok(userService.getAllUsers().stream().map(UserResponseDTO::from).toList());
        }

        // Regular users only see members of their own organization
        List<OrganizationMembership> callerMemberships = membershipRepository.findByUserId(caller.getId());
        if (callerMemberships.isEmpty()) {
            // Not in any org  -  only return self
            return ResponseEntity.ok(List.of(UserResponseDTO.from(caller)));
        }

        Long orgId = callerMemberships.get(0).getOrganization().getId();
        List<UserResponseDTO> orgUsers = membershipRepository.findByOrganizationId(orgId).stream()
                .map(m -> UserResponseDTO.from(m.getUser()))
                .toList();

        return ResponseEntity.ok(orgUsers);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequestDTO request) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        UserResponseDTO updated = userProfileService.updateProfile(user, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/me/password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequestDTO request) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        userProfileService.changePassword(user, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/sessions")
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
    public ResponseEntity<?> revokeSession(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String tokenId) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        userProfileService.revokeSession(user, tokenId);
        return ResponseEntity.noContent().build();
    }
    
    // Notification endpoints deferred to Task 6 as per C5.
}
