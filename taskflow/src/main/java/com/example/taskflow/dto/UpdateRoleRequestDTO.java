package com.example.taskflow.dto;

import com.example.taskflow.domain.OrgRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleRequestDTO {
    @NotNull(message = "Organization role is required")
    private OrgRole orgRole;
}
