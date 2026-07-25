package com.example.taskflow.service.platform.impl;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.dto.OrganizationResponseDTO;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.service.platform.PlatformOrganizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlatformOrganizationServiceImpl implements PlatformOrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public PlatformOrganizationServiceImpl(OrganizationRepository organizationRepository,
                                           OrganizationMembershipRepository membershipRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO> listAllOrganizations() {
        return organizationRepository.findAll().stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponseDTO getOrganizationAsAdmin(Long orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));
        return mapToResponseDTO(org);
    }

    @Override
    @Transactional
    public OrganizationResponseDTO suspendOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (org.getStatus() == Organization.OrgStatus.DELETED) {
            throw new IllegalStateException("Cannot suspend a deleted organization");
        }

        org.setStatus(Organization.OrgStatus.SUSPENDED);
        return mapToResponseDTO(organizationRepository.save(org));
    }

    @Override
    @Transactional
    public OrganizationResponseDTO activateOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (org.getStatus() == Organization.OrgStatus.DELETED) {
            throw new IllegalStateException("Cannot activate a deleted organization");
        }

        org.setStatus(Organization.OrgStatus.ACTIVE);
        return mapToResponseDTO(organizationRepository.save(org));
    }

    @Override
    @Transactional
    public void deleteOrganization(Long id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        org.setStatus(Organization.OrgStatus.DELETED);
        organizationRepository.save(org);
    }

    private OrganizationResponseDTO mapToResponseDTO(Organization org) {
        int memberCount = (int) membershipRepository.countByOrganizationId(org.getId());
        return new OrganizationResponseDTO(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getDescription(),
                org.getCreatedBy() != null ? org.getCreatedBy().getUsername() : null,
                org.getCreatedAt(),
                memberCount);
    }
}
