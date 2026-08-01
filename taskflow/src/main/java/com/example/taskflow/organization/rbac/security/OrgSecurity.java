package com.example.taskflow.organization.rbac.security;

import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.taskflow.identity.application.CustomUserDetailsService.CustomUserDetails;
import org.springframework.stereotype.Component;

@Component("orgSecurity")
public class OrgSecurity {

    private final OrganizationMembershipRepository membershipRepository;

    public OrgSecurity(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /**
     * Checks if the currently authenticated user is a member of the given organization.
     * Used in @PreAuthorize annotations.
     */
    public boolean isOrganizationMember(Long organizationId) {
        Long userId = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) authentication.getPrincipal()).getUser().getId();
        }
        if (userId == null || organizationId == null) {
            return false;
        }
        return membershipRepository.findByUserIdAndOrganizationId(userId, organizationId).isPresent();
    }
}
