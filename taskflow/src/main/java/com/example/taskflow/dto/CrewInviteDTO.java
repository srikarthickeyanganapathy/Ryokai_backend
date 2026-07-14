package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrewInviteDTO {
    private UUID id;
    private String email;
    private String crewName;
    private String invitedByUsername;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
