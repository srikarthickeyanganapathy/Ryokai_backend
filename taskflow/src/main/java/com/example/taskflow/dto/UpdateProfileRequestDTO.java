package com.example.taskflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateProfileRequestDTO(
    @Email(message = "Invalid email format")
    String email,

    @Size(max = 100, message = "Full name must be less than 100 characters")
    String fullName,

    @Size(max = 500, message = "Bio must be less than 500 characters")
    String bio,

    @URL(message = "Invalid avatar URL")
    String avatarUrl,

    Boolean emailNotificationsEnabled
) {
}
