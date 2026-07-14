package com.example.taskflow.dto;

import com.example.taskflow.domain.CrewVisibility;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CrewRequestDTO {

    @NotBlank(message = "Crew name is required")
    private String name;

    private String description;

    private String avatarUrl;

    private CrewVisibility visibility = CrewVisibility.INVITE_ONLY;

    private Integer memberCap = 15;
}
