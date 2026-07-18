package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ChangePasswordRequestDTO;
import com.example.taskflow.dto.SessionDTO;
import com.example.taskflow.dto.UpdateProfileRequestDTO;
import com.example.taskflow.dto.UserResponseDTO;
import com.example.taskflow.repository.RefreshTokenRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.util.JwtUtil;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public UserProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              RefreshTokenService refreshTokenService, RefreshTokenRepository refreshTokenRepository,
                              EmailService emailService, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public UserResponseDTO updateProfile(User user, UpdateProfileRequestDTO dto) {
        if (dto.email() != null && !dto.email().equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(dto.email(), user.getId())) {
                throw new IllegalArgumentException("Email is already in use");
            }
            user.setEmail(dto.email());
            user.setEmailVerified(false);
            String token = jwtUtil.generateEmailVerificationToken(user.getEmail(), user.getId());
            emailService.sendEmailVerification(user.getEmail(), user.getFullName(), token);
        }

        if (dto.fullName() != null) user.setFullName(dto.fullName());
        if (dto.bio() != null) user.setBio(dto.bio());
        if (dto.avatarUrl() != null) user.setAvatarUrl(dto.avatarUrl());
        if (dto.emailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(dto.emailNotificationsEnabled());
        }

        User savedUser = userRepository.save(user);
        return UserResponseDTO.from(savedUser);
    }

    @Transactional
    public void updateAvatarUrl(User user, String avatarUrl) {
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequestDTO dto) {
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1); // Invalidate active access tokens
        userRepository.save(user);

        // Invalidate ALL refresh tokens  -  force re-login on every device
        refreshTokenService.deleteByUserId(user.getId());

        // Send notification email (async)
        emailService.sendPasswordChangedNotification(user.getEmail(), user.getUsername(), user.getLastLoginIp());
    }

    @Transactional
    public void recordLoginTime(String username, String ip, String userAgent) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginIp(ip);
            user.setLastLoginUserAgent(userAgent);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public List<SessionDTO> getSessions(User user, String currentTokenId) {
        return refreshTokenRepository.findByUser(user).stream()
            .map(token -> new SessionDTO(
                token.getTokenId(), // Assuming the frontend gets the tokenId, or we use DB ID if needed. Let's use tokenId
                token.getDeviceInfo() != null ? token.getDeviceInfo() : "Unknown Device",
                token.getCreatedAt(),
                token.getExpiryDate(),
                token.getTokenId() != null && token.getTokenId().equals(currentTokenId) // check if it's the current one
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public void revokeSession(User user, String tokenId) {
        refreshTokenRepository.deleteByUserAndTokenId(user, tokenId);
    }

    /**
     * SEC-Min01 fix: logout-all endpoint helper.
     * Spec implies token_version should be incrementable on "logout-all / password change".
     * Previously only changePassword and resetPassword incremented token_version  - 
     * there was no way for a user who suspected compromise to invalidate all other
     * sessions' access tokens without changing their password.
     *
     * This method:
     *   1. Increments user.tokenVersion (invalidates ALL access tokens immediately)
     *   2. Deletes ALL refresh tokens for the user (forces re-login on every device)
     *
     * The caller (AuthController.logoutAll) is responsible for also denylisting
     * the current access token so the caller's own session ends immediately too.
     */
    @Transactional
    public void logoutAll(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.deleteByUserId(user.getId());
    }
}
