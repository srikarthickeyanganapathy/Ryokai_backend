package com.example.taskflow.platform.application;

import com.example.taskflow.organization.core.dto.OrganizationResponseDTO;
import java.util.List;
import com.example.taskflow.organization.core.domain.Organization;

/**
 * Control Plane organization management service interface per ADR-009.
 */
public interface PlatformOrganizationService {
    List<OrganizationResponseDTO> listAllOrganizations();
    OrganizationResponseDTO getOrganizationAsAdmin(Long orgId);
    OrganizationResponseDTO suspendOrganization(Long id);
    OrganizationResponseDTO activateOrganization(Long id);
    void deleteOrganization(Long id);
}