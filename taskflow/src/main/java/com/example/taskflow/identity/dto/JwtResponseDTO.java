package com.example.taskflow.identity.dto;
import com.example.taskflow.identity.domain.RefreshToken;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.user.dto.UserResponseDTO;

public record JwtResponseDTO(
    String accessToken,
    String refreshToken,
    long expiresIn,
    long refreshExpiresIn,
    UserResponseDTO user
) {}