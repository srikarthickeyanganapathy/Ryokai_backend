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
    private final com.example.taskflow.repository.TeamObserverRepository teamObserverRepository;

    public EmployeeStrategy(OrganizationMembershipRepository membershipRepository,
                            TeamRepository teamRepository,
                            com.example.taskflow.repository.CrewMemberRepository crewMemberRepository,
                            com.example.taskflow.repository.TeamObserverRepository teamObserverRepository) {
        this.membershipRepository = membershipRepository;
        this.teamRepository = teamRepository;
        this.crewMemberRepository = crewMemberRepository;
        this.teamObserverRepository = teamObserverRepository;
    }

    // ====================================================================
    // Helper  -  resolve the user's org role within a task's organization
    // ====================================================================
    private Role getOrgRoleInTask(User user, Task task) {
        if (user == null || task == null || task.getOrg() == null) return null;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrg()).orElse(null);
        return m != null ? m.getOrgRole() : null;
    }



    private boolean isInSameTeam(User user, Task task) {
        if (task.getTeam() == null) return false;
        return task.getTeam().getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
    }

    @Override
    public boolean isObserverVeto(User user, Task task) {
        if (task.getTeam() != null) {
            return teamObserverRepository.existsByIdTeamIdAndIdUserId(task.getTeam().getId(), user.getId());
        }
        return false;
    }

    // ====================================================================
    // RoleStrategy methods
    // ====================================================================

    @Override
    public boolean canAssign(User user) {
        return membershipRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getOrgRole() != null)
                .anyMatch(m -> hasPermission(m.getOrgRole(), "TASK_ASSIGN"));
    }

    @Override
    public boolean canReview(User user, Task task) {
        if (task == null || user == null) return false;

        if (task.isPersonal() && task.getOrg() == null && task.getCrew() == null) return false;

        // Crew tasks have no review pipeline
        if (task.getCrew() != null) return false;

        // Assignee cannot review their own work
        if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) return false;

        // Must be in the SAME org as the task
        if (task.getOrg() == null) return false;
        OrganizationMembership m = membershipRepository
                .findByUserAndOrganization(user, task.getOrg()).orElse(null);
        if (m == null || m.getOrgRole() == null) return false;

        // Verify reviewer has STRICTLY more power than assignee (revisor priority < assignee priority)
        if (task.getAssignee() != null) {
            OrganizationMembership assigneeMembership = membershipRepository
                    .findByUserAndOrganization(task.getAssignee(), task.getOrg()).orElse(null);
            if (assigneeMembership != null && assigneeMembership.getOrgRole() != null) {
                Integer reviewerPriority = m.getOrgRole().getPriority();
                Integer assigneePriority = assigneeMembership.getOrgRole().getPriority();
                if (reviewerPriority >= assigneePriority) {
                    return false; // Reviewer does not have more power
                }
            }
        }

        // Permission-based evaluation: check if the user's org role has TASK_REVIEW permission
        if (hasPermission(m.getOrgRole(), "TASK_REVIEW")) {
            // Bug #5 Fix: Require reviewer to be in the same team (or have overriding privileges)
            if (hasPermission(m.getOrgRole(), "TASK_OVERRIDE") || hasPermission(m.getOrgRole(), "PROJECT_MANAGE")) {
                return true;
            }
            return isInSameTeam(user, task);
        }
        
        return false;
    }

    @Override
    public boolean canOverride(User user) {
        // No hardcoded priority. SUPER_ADMIN is evaluated upstream, so this is just for internal task overrides.
        // For backwards compatibility on task overrides, we check if they have TASK_OVERRIDE as a proxy for high-level power.
        return membershipRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getOrgRole() != null)
                .anyMatch(m -> hasPermission(m.getOrgRole(), "TASK_OVERRIDE"));
    }

    private boolean hasPermission(Role role, String permName) {
        if (role == null || role.getPermissions() == null) return false;
        return role.getPermissions().stream().anyMatch(p -> permName.equals(p.getName()));
    }

    @Override
    public boolean canViewAllTasks(User user) {
        return membershipRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getOrgRole() != null)
                .anyMatch(m -> hasPermission(m.getOrgRole(), "TASK_VIEW"));
    }

    @Override
    public boolean canViewTask(User user, Task task) {
        if (task == null || user == null) return false;

        // Personal tasks: only creator can see, unless shared with user's crew
        if (task.isPersonal() && task.getOrg() == null && task.getCrew() == null) {
            boolean isCreator = task.getCreator() != null && task.getCreator().getId().equals(user.getId());
            if (isCreator) return true;
            
            if (task.getProject() != null && task.getProject().getSharedCrews() != null) {
                for (com.example.taskflow.domain.Crew crew : task.getProject().getSharedCrews()) {
                    if (crewMemberRepository.existsByIdCrewIdAndIdUserId(crew.getId(), user.getId())) {
                        return true;
                    }
                }
            }
            
            return false;
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

            // Permission check: can see ALL tasks in the org if they have TASK_VIEW explicitly
            Role orgRole = getOrgRoleInTask(user, task);
            if (hasPermission(orgRole, "TASK_VIEW")) return true;

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
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(user.getId());
        if (isAssignee) return true;

        return false;
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

        // Explicit permission check
        Role orgRole = getOrgRoleInTask(user, task);
        if (orgRole != null && hasPermission(orgRole, "TASK_EDIT")) {
            // Must be in same team unless they have high-level overrides
            if (hasPermission(orgRole, "TASK_OVERRIDE") || hasPermission(orgRole, "PROJECT_MANAGE")) return true;
            return isInSameTeam(user, task);
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

        // Explicit permission check
        if (task.getOrg() != null) {
            Role orgRole = getOrgRoleInTask(user, task);
            if (hasPermission(orgRole, "TASK_DELETE")) return true;
        }

        return false;
    }

    @Override
    public boolean canReassign(User user, Task task) {
        if (task == null || user == null) return false;
        
        // Creator can reassign
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) return true;

        // Crew tasks: flat structure, only creator can explicitly reassign
        if (task.getCrew() != null) return false;

        // Explicit permission check
        if (task.getOrg() != null) {
            Role orgRole = getOrgRoleInTask(user, task);
            if (hasPermission(orgRole, "TASK_REASSIGN")) return true;
        }

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

        // Explicit permission check
        if (task.getOrg() != null) {
            Role orgRole = getOrgRoleInTask(user, task);
            if (hasPermission(orgRole, "TASK_ARCHIVE")) return true;
        }

        // Assignee can archive (they can also edit, but they cannot delete)
        if (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId())) return true;

        return false;
    }

    /**
     * Spec: dependencies are "assignor-locked"  -  only the task creator
     * (assignor) and org admin/director can add/remove dependencies.
     * The assignee is explicitly blocked.
     */
    @Override
    public boolean canEditDependency(User user, Task task) {
        if (task == null || user == null) return false;

        // Creator (assignor) can always edit dependencies
        if (task.getCreator() != null && task.getCreator().getId().equals(user.getId())) return true;

        // Crew tasks: only creator can edit dependencies (flat structure, but
        // dependencies are still creator-locked)
        if (task.getCrew() != null) return false;

        // Explicit permission check
        if (task.getOrg() != null) {
            Role orgRole = getOrgRoleInTask(user, task);
            if (hasPermission(orgRole, "TASK_DEPENDENCY_EDIT")) return true;
        }

        return false;
    }
}
