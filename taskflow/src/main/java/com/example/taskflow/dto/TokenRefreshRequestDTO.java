package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;

public class TokenRefreshRequestDTO {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public TokenRefreshRequestDTO() {}

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
