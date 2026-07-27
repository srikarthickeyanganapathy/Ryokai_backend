package com.example.taskflow.dto;

public class PermissionResponseDTO {
    private Long id;
    private String name;
    private String code;
    private String module;
    private String category;
    private String description;
    private boolean system;

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

    public String getGroup() {
        return com.example.taskflow.util.PermissionMetadataRegistry.getGroup(this.code);
    }

    public String getRiskLevel() {
        return com.example.taskflow.util.PermissionMetadataRegistry.getRiskLevel(this.code);
    }

    public int getOrder() {
        return com.example.taskflow.util.PermissionMetadataRegistry.getPermissionOrder(this.code);
    }
}
