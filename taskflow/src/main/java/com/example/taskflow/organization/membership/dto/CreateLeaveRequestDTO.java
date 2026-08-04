package com.example.taskflow.organization.membership.dto;

import java.time.LocalDate;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateLeaveRequestDTO {
    private String leaveType = "VACATION";
    private String reason;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isHalfDay = false;
    private Boolean isEmergency = false;
    private String attachmentUrl;
}
