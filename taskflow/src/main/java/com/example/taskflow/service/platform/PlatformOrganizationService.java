package com.example.taskflow.service.platform;

import com.example.taskflow.dto.OrganizationResponseDTO;
import java.util.List;

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
