package com.example.taskflow.security.authorization;

import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Responsible for resolving workspace contexts for users, such as their active organization.
 * Decouples identity and workspace resolution from the authorization evaluator.
 */
@Component
public class WorkspaceContextResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceContextResolver.class);
    private final OrganizationMembershipRepository membershipRepository;

    public WorkspaceContextResolver(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    /**
     * Resolves the organization ID for a user.
     * Since Ryokai enforces one-user-one-org, this returns the single org membership's org ID.
     */
    public Long resolveOrgIdForUser(User user) {
        if (user == null || user.getId() == null) return null;
        try {
            List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
            if (!memberships.isEmpty()) {
                // One-user-one-org constraint: return the first (and only) org ID
                return memberships.get(0).getOrganization().getId();
            }
        } catch (Exception e) {
            log.debug("Could not resolve org ID for user {}: {}", user.getId(), e.getMessage());
        }
        return null;
    }
}
