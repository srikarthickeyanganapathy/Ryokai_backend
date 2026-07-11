package com.example.taskflow.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleRequestDTO {
    @NotNull(message = "Role ID is required")
    private Long roleId;
}
