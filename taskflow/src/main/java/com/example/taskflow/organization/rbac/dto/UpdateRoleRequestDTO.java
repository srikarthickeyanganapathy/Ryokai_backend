package com.example.taskflow.organization.rbac.dto;


import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.example.taskflow.organization.rbac.domain.Role;

@Data
public class UpdateRoleRequestDTO {
    @NotNull(message = "Role ID is required")
    private Long roleId;
}