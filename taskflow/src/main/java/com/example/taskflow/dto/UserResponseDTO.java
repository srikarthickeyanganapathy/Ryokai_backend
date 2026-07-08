package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.example.taskflow.domain.User;

public record UserResponseDTO(
    Long id,
    String username,
    String email,
    String fullName,
    String bio,
    String avatarUrl,
    List<String> roles,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt,
    boolean emailNotificationsEnabled,
    boolean emailVerified
) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getBio(),
            user.getAvatarUrl(),
            user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()),
            user.getCreatedAt(),
            user.getLastLoginAt(),
            user.isEmailNotificationsEnabled(),
            user.isEmailVerified()
        );
    }
}
