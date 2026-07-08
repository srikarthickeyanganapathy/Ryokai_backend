package com.example.taskflow.dto;

import com.example.taskflow.domain.TaskPriority;

import jakarta.validation.constraints.NotBlank;

public class TaskTemplateRequestDTO {

    @NotBlank(message = "Template name is required")
    private String name;

    @NotBlank(message = "Default title is required")
    private String defaultTitle;

    private String defaultDescription;

    private TaskPriority defaultPriority;

    public TaskTemplateRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDefaultTitle() { return defaultTitle; }
    public void setDefaultTitle(String defaultTitle) { this.defaultTitle = defaultTitle; }

    public String getDefaultDescription() { return defaultDescription; }
    public void setDefaultDescription(String defaultDescription) { this.defaultDescription = defaultDescription; }

    public TaskPriority getDefaultPriority() { return defaultPriority; }
    public void setDefaultPriority(TaskPriority defaultPriority) { this.defaultPriority = defaultPriority; }
}
