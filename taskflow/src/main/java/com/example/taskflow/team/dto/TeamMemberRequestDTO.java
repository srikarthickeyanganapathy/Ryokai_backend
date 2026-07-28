package com.example.taskflow.team.dto;

import jakarta.validation.constraints.NotNull;
import com.example.taskflow.user.domain.User;

public class TeamMemberRequestDTO {

    @NotNull(message = "User ID is required")
    private Long userId;

    public TeamMemberRequestDTO() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}