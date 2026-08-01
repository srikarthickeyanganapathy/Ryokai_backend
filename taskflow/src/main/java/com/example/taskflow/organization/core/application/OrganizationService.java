package com.example.taskflow.organization.core.application;

import java.util.List;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.core.dto.OrganizationResponseDTO;

public interface OrganizationService {
    OrganizationResponseDTO createOrganization(String name, String description, User adminUser);
    OrganizationResponseDTO getOrganization(Long orgId, User caller);
    List<OrganizationResponseDTO> listUserOrganizations(Long userId);
    OrganizationResponseDTO updateOrganization(Long orgId, String name, String description, User caller);
    OrganizationResponseDTO getUserOrganization(Long userId);
}
