package com.example.taskflow.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponseDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
    private int memberCount;
}
