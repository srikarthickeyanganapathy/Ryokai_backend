package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CrewMessageRequestDTO {

    @NotBlank(message = "Message content is required")
    private String content;

    private Long taskId;
}
