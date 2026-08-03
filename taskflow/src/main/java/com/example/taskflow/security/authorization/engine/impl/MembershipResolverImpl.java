package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.engine.MembershipResolver;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.NumberUtils;

@Component
public class MembershipResolverImpl implements MembershipResolver {

    private final OrganizationMembershipRepository orgRepo;
    private final CrewMemberRepository crewRepo;
    private final com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository organizationRepository;

    public MembershipResolverImpl(OrganizationMembershipRepository orgRepo, 
                                  CrewMemberRepository crewRepo,
                                  com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository organizationRepository) {
        this.orgRepo = orgRepo;
        this.crewRepo = crewRepo;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public AuthorizationDecision resolveMembership(AuthorizationRequest request) {
        if (request.getUser() == null || request.getUser().getId() == null) {
            return AuthorizationDecision.deny("MEMBERSHIP", "Anonymous access is not allowed");
        }

        if (request.getUser().isSuperAdmin()) {
            return AuthorizationDecision.allow("MEMBERSHIP", "SuperAdmin bypass");
        }

        switch (request.getWorkspaceType()) {
            case PERSONAL:
                return AuthorizationDecision.allow("MEMBERSHIP", "User granted access in Personal workspace");

            case CREW:
                Long crewId = extractId(request, "crewId");
                if (crewId != null && crewRepo.existsByIdCrewIdAndIdUserId(crewId, request.getUser().getId())) {
                    return AuthorizationDecision.allow("MEMBERSHIP", "User is a member of the Crew");
                }
                return AuthorizationDecision.deny("MEMBERSHIP", "User is not a Crew member");

            case ORGANIZATION:
                Long orgId = extractId(request, "organizationId");
                if (orgId == null) {
                     return AuthorizationDecision.deny("MEMBERSHIP", "Organization ID required for org-scoped check");
                }
                com.example.taskflow.organization.core.domain.Organization org = organizationRepository.findById(orgId).orElse(null);
                if (org == null) {
                     return AuthorizationDecision.deny("MEMBERSHIP", "Organization not found");
                }
                if (org.getStatus() != com.example.taskflow.organization.core.domain.Organization.OrgStatus.ACTIVE) {
                     return AuthorizationDecision.deny("MEMBERSHIP", "Organization is suspended");
                }
                if (orgRepo.existsByUserIdAndOrganizationId(request.getUser().getId(), orgId)) {
                    if (request.getAction() != null && request.getAction().isMembershipIntrinsic()) {
                        return AuthorizationDecision.allow("MEMBERSHIP", "User is an Organization member (Intrinsic)");
                    }
                    return AuthorizationDecision.abstain("User is an Organization member evaluating non-intrinsic action");
                }
                return AuthorizationDecision.deny("MEMBERSHIP", "User is not an Organization member");
                
            default:
                return AuthorizationDecision.deny("MEMBERSHIP", "Unknown workspace type");
        }
    }

    private Long extractId(AuthorizationRequest request, String key) {
        Object idObj = request.getContext().get(key);
        if (idObj instanceof Number number) {
            return NumberUtils.convertNumberToTargetClass(number, Long.class);
        }
        return null;
    }

}