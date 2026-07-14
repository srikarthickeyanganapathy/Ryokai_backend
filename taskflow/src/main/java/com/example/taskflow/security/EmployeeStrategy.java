package com.example.taskflow.security;

import com.example.taskflow.domain.OrganizationMembership;
import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.repository.TeamRepository;
import org.springframework.stereotype.Component;

@Component
public class EmployeeStrategy implements RoleStrategy {

    private final OrganizationMembershipRepository membershipRepository;
    private final TeamRepository teamRepository;
    private final com.example.taskflow.repository.CrewMemberRepository crewMemberRepository;

    public EmployeeStrategy(OrganizationMembershipRepository membershipRepository,
                            TeamRepository teamRepository,
                            com.example.taskflow.repository.CrewMemberRepository crewMemberRepository) {
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.crewMemberRepository = crewMemberRepository;
    }

    // ====================================================================
    // Helper — resolve the user's org role within a task's organization
    // ====================================================================
    private Role getOrgRoleInTask(User user, Task task) {
        if (user == null || task == null || task.getOrg() == null) return null;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrg()).orElse(null);
        return m != null ? m.getOrgRole() : null;
    }

    private boolean isOrgAdminOrAbove(User user, Task task) {
        Role role = getOrgRoleInTask(user, task);
        return role != null && role.isBuiltinDirectorOrAbove();
    }

    private boolean isOrgManagerOrAbove(User user, Task task) {
        Role role = getOrgRoleInTask(user, task);
        return role != null && role.isBuiltinManagerOrAbove();
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
                .anyMatch(m -> m.getOrgRole().isBuiltinManagerOrAbove());
    }

    @Override
    public boolean canReview(User user, Task task) {
        if (task == null || user == null) return false;

        if (task.isPersonal() && task.getOrg() == null && task.getCrew() == null) return false;

        // Crew tasks have no review pipeline
        if (task.getCrew() != null) return false;

        // Assignee cannot review their own work
        if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) return false;

        // SM-M02 fix: creator cannot review their own task (spec:
        // "creator ≠ reviewer (no self-review)"). Previously only the
        // assignee was blocked; a creator who held MANAGER+ role in the
        // same org could approve or reject their own submission.
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) return false;

        // Must be in the SAME org as the task
        if (task.getOrg() == null) return false;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrg()).orElse(null);
        if (m == null || m.getOrgRole() == null) return false;

        // Managers and above can review tasks inherently (aligning with frontend canReview flag)
        if (m.getOrgRole().isBuiltinManagerOrAbove()) {
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
                .anyMatch(m -> m.getOrgRole().isBuiltinDirectorOrAbove());
    }

    @Override
    public boolean canViewTask(User user, Task task) {
        if (task == null || user == null) return false;

        // Personal tasks: only creator can see
        if (task.isPersonal() && task.getOrg() == null && task.getCrew() == null) {
            return task.getCreator() != null && task.getCreator().getId().equals(user.getId());
        }

        // Crew tasks: all crew members can view
        if (task.getCrew() != null) {
            return crewMemberRepository.existsByIdCrewIdAndIdUserId(task.getCrew().getId(), user.getId());
        }

        // Org tasks: visibility depends on role
        if (task.getOrg() != null) {
            // Must be in the same org first
            if (!membershipRepository.existsByUserAndOrganization(user, task.getOrg())) {
                return false;
            }

            // Admin/Director: can see ALL tasks in the org (transparency/oversight)
            if (isOrgAdminOrAbove(user, task)) return true;

            // Creator or assignee can always see their own tasks
            boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(user.getId());
            boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
            if (isCreator || isAssignee) return true;

            // Team-scoped: if the task belongs to a team, only team members can see it
            if (task.getTeam() != null) {
                return isInSameTeam(user, task);
            }

            // Org-wide task (no team): visible to all org members
            return true;
        }

        // Fallback: only the assignee
        return task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
    }

    @Override
    public boolean canEdit(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can always edit
        boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(user.getId());
        if (isCreator) return true;

        // Crew tasks: all crew members can edit
        if (task.getCrew() != null) {
            return crewMemberRepository.existsByIdCrewIdAndIdUserId(task.getCrew().getId(), user.getId());
        }

        // Assignee can edit their own task
        if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) return true;

        // Org admin/director/manager can edit tasks in their org
        if (task.getOrg() != null && isOrgManagerOrAbove(user, task)) {
            Role role = getOrgRoleInTask(user, task);
            if (role != null && role.isBuiltinDirectorOrAbove()) return true;
            if (role != null && role.isBuiltinManagerOrAbove()) return isInSameTeam(user, task);
        }

        return false;
    }

    @Override
    public boolean canDelete(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can delete
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) return true;

        // Crew tasks: only creator can delete (already handled above, so if it's a crew task and not creator, deny)
        if (task.getCrew() != null) return false;

        // Org admin/director can delete any task in their org
        if (task.getOrg() != null && isOrgAdminOrAbove(user, task)) return true;

        return false;
    }

    @Override
    public boolean canReassign(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can reassign
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) return true;

        // Crew tasks: flat structure, only creator can explicitly reassign
        if (task.getCrew() != null) return false;

        // Org admin/director can reassign any task in their org
        if (task.getOrg() != null && isOrgAdminOrAbove(user, task)) return true;

        return false;
    }

    /**
     * RB-M04 fix: dedicated archive permission.
     * Stricter than canEdit (assignee can edit but not archive),
     * looser than canDelete (org manager can archive but not delete).
     */
    @Override
    public boolean canArchive(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator can always archive
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) return true;

        // Crew tasks: only creator can archive (already handled above, so deny)
        if (task.getCrew() != null) return false;

        // Org admin/director/manager can archive any task in their org
        if (task.getOrg() != null && isOrgManagerOrAbove(user, task)) return true;

        // Assignee can archive (they can also edit, but they cannot delete)
        if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) return true;

        return false;
    }
}
