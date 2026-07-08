package com.example.taskflow.dto;

public record JwtResponseDTO(
    String accessToken,
    String refreshToken,
    long expiresIn,
    long refreshExpiresIn,
    UserResponseDTO user
) {}
