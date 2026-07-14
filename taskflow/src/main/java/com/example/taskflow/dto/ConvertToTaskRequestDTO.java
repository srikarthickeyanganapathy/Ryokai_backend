package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConvertToTaskRequestDTO {

    @NotBlank(message = "Task title is required")
    private String title;

    private String description;
}
