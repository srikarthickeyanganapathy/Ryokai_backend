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

        if (user.isSuperAdmin()) {
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

}
