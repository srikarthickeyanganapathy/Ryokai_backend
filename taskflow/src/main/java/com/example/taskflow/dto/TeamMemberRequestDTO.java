package com.example.taskflow.dto;

import jakarta.validation.constraints.NotNull;

public class TeamMemberRequestDTO {

    @NotNull(message = "User ID is required")
    private Long userId;

    public TeamMemberRequestDTO() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
