package com.example.taskflow.dto;

import java.util.List;

public class PermissionCatalogResponseDTO {
    private int catalogVersion;
    private String generatedAt;
    private List<PermissionModuleDTO> modules;

    public PermissionCatalogResponseDTO() {}

    public PermissionCatalogResponseDTO(int catalogVersion, String generatedAt, List<PermissionModuleDTO> modules) {
        this.catalogVersion = catalogVersion;
        this.generatedAt = generatedAt;
        this.modules = modules;
    }

    public int getCatalogVersion() { return catalogVersion; }
    public void setCatalogVersion(int catalogVersion) { this.catalogVersion = catalogVersion; }

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    
    public List<PermissionModuleDTO> getModules() { return modules; }
    public void setModules(List<PermissionModuleDTO> modules) { this.modules = modules; }
}

