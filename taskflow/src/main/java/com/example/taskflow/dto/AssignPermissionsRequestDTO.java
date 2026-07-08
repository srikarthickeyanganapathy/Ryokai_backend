package com.example.taskflow.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssignPermissionsRequestDTO(
    @NotNull(message = "Permissions list cannot be null")
    List<String> permissionNames
) {}
