package com.example.taskflow.dto;

import java.util.Set;

public class RoleResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Set<PermissionResponseDTO> permissions;

    public RoleResponseDTO() {}
    public RoleResponseDTO(Long id, String name, String description, Set<PermissionResponseDTO> permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.permissions = permissions;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<PermissionResponseDTO> getPermissions() { return permissions; }
    public void setPermissions(Set<PermissionResponseDTO> permissions) { this.permissions = permissions; }
}
