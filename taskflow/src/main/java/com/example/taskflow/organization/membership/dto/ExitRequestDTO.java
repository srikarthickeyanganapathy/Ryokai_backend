package com.example.taskflow.organization.membership.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ExitRequestDTO {
    private Long id;
    private Long userId;
    private String username;
    private Long organizationId;
    private String organizationName;
    private String reason;
    private String status;
    private String decisionComment;
    private String reviewedBy;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private LocalDate effectiveExitDate;
}
