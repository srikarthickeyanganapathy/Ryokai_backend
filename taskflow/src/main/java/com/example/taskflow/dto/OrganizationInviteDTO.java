package com.example.taskflow.dto;

import java.time.LocalDateTime;

public record OrganizationInviteDTO(
    Long id,
    Long organizationId,
    String organizationName,
    String invitedByUsername,
    String inviteeUsername,
    String orgRole,
    String status,
    String token,
    LocalDateTime expiresAt,
    LocalDateTime createdAt
) {}
