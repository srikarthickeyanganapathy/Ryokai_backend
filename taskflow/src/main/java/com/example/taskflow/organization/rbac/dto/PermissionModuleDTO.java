package com.example.taskflow.organization.rbac.dto;

import java.util.List;

public class PermissionModuleDTO {
    private String moduleCode;
    private String displayName;
    private int order;
    private String description;
    private List<PermissionResponseDTO> permissions;

    public PermissionModuleDTO() {}

    public PermissionModuleDTO(String moduleCode, String displayName, int order, String description, List<PermissionResponseDTO> permissions) {
        this.moduleCode = moduleCode;
        this.displayName = displayName;
        this.order = order;
        this.description = description;
        this.permissions = permissions;
    }

    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<PermissionResponseDTO> getPermissions() { return permissions; }
    public void setPermissions(List<PermissionResponseDTO> permissions) { this.permissions = permissions; }
}
