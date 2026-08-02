package com.example.taskflow.organization.rbac.dto;

public record ResourceLookupDTO(
    Long id,
    String name,
    String subtitle,
    String icon,
    String status
) {}
