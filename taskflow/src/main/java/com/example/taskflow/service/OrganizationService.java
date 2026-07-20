package com.example.taskflow.service;

import java.util.List;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.OrganizationResponseDTO;

public interface OrganizationService {
    OrganizationResponseDTO createOrganization(String name, String description, User adminUser);
    OrganizationResponseDTO getOrganization(Long orgId, User caller);
    List<OrganizationResponseDTO> listUserOrganizations(Long userId);
    OrganizationResponseDTO getUserOrganization(Long userId);
    List<OrganizationResponseDTO> listAllOrganizations();
    OrganizationResponseDTO getOrganizationAsAdmin(Long orgId);
    OrganizationResponseDTO suspendOrganization(Long id);
    OrganizationResponseDTO activateOrganization(Long id);
    void deleteOrganization(Long id);
}
