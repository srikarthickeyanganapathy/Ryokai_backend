package com.example.taskflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long organizationId;
    private String organizationName;
    private int memberCount;
    private java.util.List<UserSummaryDTO> members;
}
