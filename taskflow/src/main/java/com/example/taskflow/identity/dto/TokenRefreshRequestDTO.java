package com.example.taskflow.identity.dto;

import jakarta.validation.constraints.NotBlank;
import com.example.taskflow.identity.domain.RefreshToken;

public class TokenRefreshRequestDTO {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    public TokenRefreshRequestDTO() {}

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}