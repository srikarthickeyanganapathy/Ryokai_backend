package com.example.taskflow.security;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.OrgRole;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.TeamRepository;
import org.springframework.stereotype.Component;

@Component
public class EmployeeStrategy implements RoleStrategy {

    private final OrganizationMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;

    public EmployeeStrategy(OrganizationMembershipRepository membershipRepository,
                            TeamRepository teamRepository) {
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
    }

    // ====================================================================
    // Helper — resolve the user's org role within a task's organization
    // ====================================================================
    private OrgRole getOrgRoleInTask(User user, Task task) {
        if (user == null || task == null || task.getOrganization() == null) return null;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrganization()).orElse(null);
        return m != null ? m.getOrgRole() : null;
    }

    private boolean isOrgAdminOrAbove(User user, Task task) {
        OrgRole role = getOrgRoleInTask(user, task);
        return role == OrgRole.ADMIN || role == OrgRole.DIRECTOR;
    }

    private boolean isOrgManagerOrAbove(User user, Task task) {
        OrgRole role = getOrgRoleInTask(user, task);
        return role == OrgRole.ADMIN || role == OrgRole.DIRECTOR || role == OrgRole.MANAGER;
    }

    private boolean isInSameTeam(User user, Task task) {
        if (task.getTeam() == null) return false;
        return task.getTeam().getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
    }

    // ====================================================================
    // RoleStrategy methods
    // ====================================================================

    @Override
    public boolean canAssign(User user) {
        // Only org members with MANAGER/DIRECTOR/ADMIN can assign
        return membershipRepository.findByUserId(user.getId()).stream()
                .anyMatch(m -> m.getOrgRole() == OrgRole.ADMIN
                        || m.getOrgRole() == OrgRole.DIRECTOR
                        || m.getOrgRole() == OrgRole.MANAGER);
    }

    @Override
    public boolean canReview(User user, Task task) {
        if (task == null || user == null) return false;
        if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId())) return false;

        // Must be in the SAME org and have elevated role
        if (task.getOrganization() == null) return false;
        OrgRole role = getOrgRoleInTask(user, task);
        if (role == null) return false;

        // ADMIN or DIRECTOR can review anything in the org
        if (role == OrgRole.ADMIN || role == OrgRole.DIRECTOR) return true;

        // MANAGER can review if they're in the same team as the assignee
        if (role == OrgRole.MANAGER) {
            return isInSameTeam(user, task);
        }

        return false;
    }

    @Override
    public boolean canOverride(User user) {
        return membershipRepository.findByUserId(user.getId()).stream()
                .anyMatch(m -> m.getOrgRole() == OrgRole.ADMIN
                        || m.getOrgRole() == OrgRole.DIRECTOR);
    }

    @Override
    public boolean canViewTask(User user, Task task) {
        if (task == null || user == null) return false;

        // Personal tasks: only creator can see
        if (task.isPersonal() && task.getOrganization() == null) {
            return task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
        }

        // Org tasks: visible to any member of the same org
        if (task.getOrganization() != null) {
            return membershipRepository.existsByUserAndOrganization(user, task.getOrganization());
        }

        // Fallback: only the assignee
        return task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId());
    }

    @Override
    public boolean canEdit(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can always edit
        boolean isCreator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
        if (isCreator) return true;

        // Assignee can edit their own task
        if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId())) return true;

        // Org admin/director/manager can edit tasks in their org
        if (task.getOrganization() != null && isOrgManagerOrAbove(user, task)) {
            // Manager must be in the same team
            OrgRole role = getOrgRoleInTask(user, task);
            if (role == OrgRole.ADMIN || role == OrgRole.DIRECTOR) return true;
            if (role == OrgRole.MANAGER) return isInSameTeam(user, task);
        }

        return false;
    }

    @Override
    public boolean canDelete(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can delete
        if (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId())) return true;

        // Org admin/director can delete any task in their org
        if (task.getOrganization() != null && isOrgAdminOrAbove(user, task)) return true;

        return false;
    }

    @Override
    public boolean canReassign(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can reassign
        if (task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId())) return true;

        // Org admin/director can reassign any task in their org
        if (task.getOrganization() != null && isOrgAdminOrAbove(user, task)) return true;

        return false;
    }
}
