package com.example.taskflow.security;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RoleStrategyFactory {

    private final EmployeeStrategy employeeStrategy;
    private final SuperAdminStrategy superAdminStrategy;
    private final OrganizationMembershipRepository membershipRepository;

    public RoleStrategyFactory(EmployeeStrategy employeeStrategy,
            SuperAdminStrategy superAdminStrategy,
            OrganizationMembershipRepository membershipRepository) {
        this.employeeStrategy = employeeStrategy;
        this.superAdminStrategy = superAdminStrategy;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Returns the strategy for a user.
     * SUPER_ADMIN is the ONLY global role. All other access is determined
     * by org-scoped membership roles.
     */
    public RoleStrategy getStrategy(User user) {
        if (user == null) return employeeStrategy;

        // SUPER_ADMIN — the only global privileged role (platform owner)
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> {
                    String name = role.getName();
                    if (name.startsWith("ROLE_")) return name.substring(5);
                    return name;
                })
                .collect(Collectors.toSet());

        if (roleNames.contains("SUPER_ADMIN")) {
            return superAdminStrategy;
        }

        // Everyone else uses the same base strategy.
        // Actual permissions are determined by org membership (ADMIN/DIRECTOR/MANAGER/EMPLOYEE)
        // checked inside the strategy methods.
        return employeeStrategy;
    }

    /** Get the org-scoped role for a user within a specific organization */
    public OrganizationMembership getMembership(User user, Organization org) {
        if (user == null || org == null) return null;
        return membershipRepository.findByUserAndOrganization(user, org).orElse(null);
    }

    /** Check if user has a specific org-role in any organization */
    public boolean hasOrgRole(User user, com.example.taskflow.domain.OrgRole role) {
        if (user == null) return false;
        return membershipRepository.findByUserId(user.getId()).stream()
                .anyMatch(m -> m.getOrgRole() == role);
    }

    /** Returns the user's organization membership, or null if independent */
    public OrganizationMembership getUserMembership(User user) {
        if (user == null) return null;
        var memberships = membershipRepository.findByUserId(user.getId());
        return memberships.isEmpty() ? null : memberships.get(0);
    }

    /** Checks whether a user belongs to any organization */
    public boolean isOrgMember(User user) {
        if (user == null) return false;
        return !membershipRepository.findByUserId(user.getId()).isEmpty();
    }
}
