package com.example.taskflow.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InviteMemberRequestDTO {

    @NotBlank(message = "Username is required")
    private String username;

    @NotNull(message = "Role ID is required")
    private Long roleId;

    public InviteMemberRequestDTO() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
