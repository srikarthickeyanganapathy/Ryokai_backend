package com.example.taskflow.organization.rbac.dto;

import java.util.Set;

public class RoleResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Set<RolePermissionAssignmentDTO> permissions;

    private Long organizationId;
    private String organizationName;
    private Integer priority;

    public RoleResponseDTO() {}
    public RoleResponseDTO(Long id, String name, String description, Set<RolePermissionAssignmentDTO> permissions) {
        this(id, name, description, permissions, null, null, 100);
    }
    
    public RoleResponseDTO(Long id, String name, String description, Set<RolePermissionAssignmentDTO> permissions, Long organizationId, String organizationName) {
        this(id, name, description, permissions, organizationId, organizationName, 100);
    }

    public RoleResponseDTO(Long id, String name, String description, Set<RolePermissionAssignmentDTO> permissions, Long organizationId, String organizationName, Integer priority) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.permissions = permissions;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.priority = priority;
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<RolePermissionAssignmentDTO> getPermissions() { return permissions; }
    public void setPermissions(Set<RolePermissionAssignmentDTO> permissions) { this.permissions = permissions; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
