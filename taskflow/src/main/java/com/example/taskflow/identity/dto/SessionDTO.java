package com.example.taskflow.identity.dto;

import java.time.LocalDateTime;

public record SessionDTO(
    String tokenId,
    String deviceInfo,
    LocalDateTime createdAt,
    LocalDateTime expiresAt,
    boolean current
) {
}
