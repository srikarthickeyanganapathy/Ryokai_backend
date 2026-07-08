package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleCreateRequestDTO(
    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    @Pattern(regexp = "^(?!ROLE_)[A-Z0-9_]+$", message = "Role name must be uppercase, contain only letters/numbers/underscores, and cannot start with ROLE_")
    String name,
    
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    String description
) {}
