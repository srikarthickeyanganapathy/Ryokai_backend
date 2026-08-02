package com.example.taskflow.organization.rbac.dto;

import java.util.List;

public class PermissionResponseDTO {
    private Long id;
    private String name;
    private String code;
    private String module;
    private String category;
    private String description;
    private boolean system;
    private String scope;

    public PermissionResponseDTO() {}
    public PermissionResponseDTO(Long id, String name, String code, String module, String category, String description, boolean system) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.module = module;
        this.category = category;
        this.description = description;
        this.system = system;
    }
    public PermissionResponseDTO(Long id, String name, String code, String module, String category, String description, boolean system, String scope) {
        this(id, name, code, module, category, description, system);
        this.scope = scope;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getGroup() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getGroup(this.code);
    }

    public String getRiskLevel() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getRiskLevel(this.code);
    }

    public int getOrder() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getPermissionOrder(this.code);
    }

    public List<String> getSupportedScopes() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getSupportedScopes(this.code);
    }

    public boolean isScopeRequired() {
        return com.example.taskflow.security.PermissionMetadataRegistry.isScopeRequired(this.code);
    }

    public List<String> getRequires() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getRequires(this.code);
    }

    public String getRecommendedScope() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getRecommendedScope(this.code);
    }

    public String getActionRules() {
        return com.example.taskflow.security.PermissionMetadataRegistry.getActionRules(this.code);
    }

    public boolean isRequiresResourceAssignment() {
        return com.example.taskflow.security.PermissionMetadataRegistry.requiresResourceAssignment(this.code);
    }
}
