package com.example.taskflow.security;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.RoleCategory;
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
    private RoleCategory getOrgRoleCategoryInTask(User user, Task task) {
        if (user == null || task == null || task.getOrganization() == null) return null;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrganization()).orElse(null);
        return m != null && m.getOrgRole() != null ? m.getOrgRole().getCategory() : null;
    }

    private boolean isOrgAdminOrAbove(User user, Task task) {
        RoleCategory role = getOrgRoleCategoryInTask(user, task);
        return role == RoleCategory.BUILTIN_ADMIN || role == RoleCategory.BUILTIN_DIRECTOR;
    }

    private boolean isOrgManagerOrAbove(User user, Task task) {
        RoleCategory role = getOrgRoleCategoryInTask(user, task);
        return role == RoleCategory.BUILTIN_ADMIN || role == RoleCategory.BUILTIN_DIRECTOR || role == RoleCategory.BUILTIN_MANAGER;
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
                .filter(m -> m.getOrgRole() != null)
                .anyMatch(m -> m.getOrgRole().getCategory() == RoleCategory.BUILTIN_ADMIN
                        || m.getOrgRole().getCategory() == RoleCategory.BUILTIN_DIRECTOR
                        || m.getOrgRole().getCategory() == RoleCategory.BUILTIN_MANAGER);
    }

    @Override
    public boolean canReview(User user, Task task) {
        if (task == null || user == null) return false;

        // Personal tasks have no review pipeline (TODO → COMPLETED, no reviewer)
        if (task.isPersonal() && task.getOrganization() == null) return false;

        // Assignee cannot review their own work
        if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId())) return false;

        // Must be in the SAME org as the task
        if (task.getOrganization() == null) return false;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrganization()).orElse(null);
        if (m == null || m.getOrgRole() == null) return false;

        // Managers and above can review tasks inherently (aligning with frontend canReview flag)
        RoleCategory category = m.getOrgRole().getCategory();
        if (category == RoleCategory.BUILTIN_ADMIN || 
            category == RoleCategory.BUILTIN_DIRECTOR || 
            category == RoleCategory.BUILTIN_MANAGER) {
            return true;
        }

        // Permission-based fallback: check if the user's org role has TASK_REVIEW permission
        return m.getOrgRole().getPermissions().stream()
                .anyMatch(p -> "TASK_REVIEW".equals(p.getName()));
    }

    @Override
    public boolean canOverride(User user) {
        return membershipRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getOrgRole() != null)
                .anyMatch(m -> m.getOrgRole().getCategory() == RoleCategory.BUILTIN_ADMIN
                        || m.getOrgRole().getCategory() == RoleCategory.BUILTIN_DIRECTOR);
    }

    @Override
    public boolean canViewTask(User user, Task task) {
        if (task == null || user == null) return false;

        // Personal tasks: only creator can see
        if (task.isPersonal() && task.getOrganization() == null) {
            return task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
        }

        // Org tasks: visibility depends on role
        if (task.getOrganization() != null) {
            // Must be in the same org first
            if (!membershipRepository.existsByUserAndOrganization(user, task.getOrganization())) {
                return false;
            }

            // Admin/Director: can see ALL tasks in the org (transparency/oversight)
            if (isOrgAdminOrAbove(user, task)) return true;

            // Creator or assignee can always see their own tasks
            boolean isCreator = task.getCreatedBy() != null && task.getCreatedBy().getId().equals(user.getId());
            boolean isAssignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId());
            if (isCreator || isAssignee) return true;

            // Team-scoped: if the task belongs to a team, only team members can see it
            if (task.getTeam() != null) {
                return isInSameTeam(user, task);
            }

            // Org-wide task (no team): visible to all org members
            return true;
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
            RoleCategory role = getOrgRoleCategoryInTask(user, task);
            if (role == RoleCategory.BUILTIN_ADMIN || role == RoleCategory.BUILTIN_DIRECTOR) return true;
            if (role == RoleCategory.BUILTIN_MANAGER) return isInSameTeam(user, task);
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
