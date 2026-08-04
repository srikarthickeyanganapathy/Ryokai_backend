package com.example.taskflow.organization.membership.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateExitRequestDTO {
    private String reason;
    private String optionalComments;
}
