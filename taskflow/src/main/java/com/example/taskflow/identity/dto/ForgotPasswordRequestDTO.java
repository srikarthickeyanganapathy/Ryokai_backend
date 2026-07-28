package com.example.taskflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequestDTO(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email
) {
}
