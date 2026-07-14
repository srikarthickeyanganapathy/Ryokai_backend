package com.example.taskflow.security;

import org.springframework.stereotype.Component;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

/**
 * Super Admin Strategy â€” Privacy-compliant platform owner role.
 * 
 * The Super Admin (platform owner) can:
 *   - Manage their OWN personal tasks (create, edit, complete, delete)
 *   - View platform-level metadata (user counts, org listing, security audit)
 *   - Suspend/activate/delete organizations via AdminController
 * 
 * The Super Admin CANNOT:
 *   - View, edit, review, or interact with ANY org task data
 *   - This is a privacy boundary â€” org data belongs to the org
 */
@Component
public class SuperAdminStrategy implements RoleStrategy {

    @Override
    public boolean canAssign(User user) {
        // Super Admin does NOT assign tasks within orgs
        return false;
    }

    @Override
    public boolean canReview(User user, Task task) {
        // Personal tasks have no review pipeline (TODO â†’ COMPLETED)
        // Org tasks: Super Admin cannot review â€” privacy boundary
        return false;
    }

    @Override
    public boolean canOverride(User user) {
        // Used for platform admin features (org management, user listing)
        // NOT used for task data access
        return true;
    }

    @Override
    public boolean canViewTask(User user, Task task) {
        // Privacy: Super Admin can ONLY see their own personal tasks
        if (task == null || user == null) return false;
        return isOwnTask(user, task);
    }
    
    @Override
    public boolean canEdit(User user, Task task) {
        if (task == null || user == null) return false;
        return isOwnPersonalTask(user, task);
    }

    @Override
    public boolean canDelete(User user, Task task) {
        if (task == null || user == null) return false;
        return isOwnPersonalTask(user, task);
    }

    @Override
    public boolean canReassign(User user, Task task) {
        // Personal tasks can't be reassigned (self-only)
        return false;
    }

    // RB-M04 fix: dedicated archive permission.
    @Override
    public boolean canArchive(User user, Task task) {
        if (task == null || user == null) return false;
        // Privacy boundary: Super Admin can only archive their own personal tasks.
        return isOwnPersonalTask(user, task);
    }

    // --- Private helpers ---

    private boolean isOwnTask(User user, Task task) {
        return (task.getCreator() != null && task.getCreator().getId().equals(user.getId()))
            || (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId()));
    }

    private boolean isOwnPersonalTask(User user, Task task) {
        return task.isPersonal() && task.getOrg() == null && isOwnTask(user, task);
    }
}
