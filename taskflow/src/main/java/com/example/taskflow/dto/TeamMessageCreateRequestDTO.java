package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMessageCreateRequestDTO {
    @NotBlank(message = "Message content cannot be blank")
    private String content;
}
