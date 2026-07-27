package com.example.taskflow.dto;

import java.util.List;

public class PermissionCatalogResponseDTO {
    private int version;
    private List<PermissionModuleDTO> modules;

    public PermissionCatalogResponseDTO() {}

    public PermissionCatalogResponseDTO(int version, List<PermissionModuleDTO> modules) {
        this.version = version;
        this.modules = modules;
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    
    public List<PermissionModuleDTO> getModules() { return modules; }
    public void setModules(List<PermissionModuleDTO> modules) { this.modules = modules; }
}
