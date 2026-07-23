package com.example.taskflow.security;

import java.time.LocalDateTime;

public record ImpersonationSession(
    Long adminUserId,
    Long targetUserId,
    String reason,
    String ticketId,
    LocalDateTime expiresAt
) {
    public boolean isValid() {
        return expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }
}
