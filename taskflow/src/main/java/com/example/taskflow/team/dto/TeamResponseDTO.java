package com.example.taskflow.team.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.taskflow.user.dto.UserSummaryDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponseDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long organizationId;
    private String organizationName;
    private int memberCount;
    private java.util.List<UserSummaryDTO> members;
}