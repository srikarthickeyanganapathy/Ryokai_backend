package com.example.taskflow.dto;

import com.example.taskflow.domain.OrgRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InviteMemberRequestDTO {

    @NotBlank(message = "Username is required")
    private String username;

    @NotNull(message = "Organization role is required")
    private OrgRole orgRole;

    public InviteMemberRequestDTO() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public OrgRole getOrgRole() { return orgRole; }
    public void setOrgRole(OrgRole orgRole) { this.orgRole = orgRole; }
}
