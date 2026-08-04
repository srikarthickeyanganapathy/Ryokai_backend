package com.example.taskflow.organization.membership.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LeaveRequestDTO {
    private Long id;
    private Long userId;
    private String username;
    private Long organizationId;
    private String organizationName;
    private String leaveType;
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer workingDays;
    private Integer calendarDays;
    private Boolean isHalfDay;
    private Boolean isEmergency;
    private String attachmentUrl;
    private String status;
    private String adminComment;

    @JsonProperty("reviewedBy")
    @JsonAlias({"decidedBy", "decided_by"})
    private String reviewedBy;

    private LocalDateTime createdAt;

    @JsonProperty("reviewedAt")
    @JsonAlias({"decidedAt", "decided_at"})
    private LocalDateTime reviewedAt;

    @JsonProperty("decidedBy")
    public String getDecidedBy() { return reviewedBy; }

    @JsonProperty("decidedAt")
    public LocalDateTime getDecidedAt() { return reviewedAt; }
}
