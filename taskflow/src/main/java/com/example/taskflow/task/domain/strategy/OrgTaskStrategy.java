package com.example.taskflow.task.domain.strategy;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskMode;
import com.example.taskflow.task.domain.model.TaskStatus;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.api.request.TaskRequestDTO;
import com.example.taskflow.organization.rbac.application.PermissionService;
import com.example.taskflow.security.PermissionCode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;

@Component
public class OrgTaskStrategy implements TaskLifecycleStrategy, TaskScopeBehavior, Approvable {

    private final PermissionService permissionService;
    private final com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository membershipRepository;

    public OrgTaskStrategy(PermissionService permissionService,
                           com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository membershipRepository) {
        this.permissionService = permissionService;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public TaskMode getSupportedMode() {
        return TaskMode.ORG;
    }

    @Override
    public boolean canCreate(User u, TaskRequestDTO request) {
        if (u == null) return false;
        Long orgId = request != null ? request.getOrgId() : null;
        if (orgId == null && u != null) {
            var memberships = membershipRepository.findByUserId(u.getId());
            if (!memberships.isEmpty()) {
                orgId = memberships.get(0).getOrganization().getId();
            }
        }
        if (orgId == null) return false;
        return permissionService.isAuthorized(u, PermissionCode.TASK_CREATE, orgId);
    }

    private boolean check(User u, Task t, PermissionCode code) {
        if (t.getOrg() == null) return false;
        return permissionService.isAuthorized(u, code, t.getOrg().getId(), "TASK", t.getId());
    }

    @Override
    public boolean canView(User u, Task t) {
        return check(u, t, PermissionCode.TASK_VIEW);
    }

    @Override
    public boolean canReassign(User u, Task t) {
        return check(u, t, PermissionCode.TASK_REASSIGN);
    }

    @Override
    public boolean canArchive(User u, Task t) {
        return check(u, t, PermissionCode.TASK_ARCHIVE);
    }

    @Override
    public boolean canEditDependency(User u, Task t) {
        return check(u, t, PermissionCode.TASK_DEPENDENCY_UPDATE);
    }

    @Override
    public boolean canEdit(User u, Task t) {
        return check(u, t, PermissionCode.TASK_UPDATE);
    }

    @Override
    public boolean canDelete(User u, Task t) {
        return check(u, t, PermissionCode.TASK_DELETE);
    }

    @Override
    public boolean validateDependencyLink(Task source, Task target) {
        return target.getMode() == TaskMode.ORG &&
               target.getOrg() != null &&
               source.getOrg() != null &&
               target.getOrg().getId().equals(source.getOrg().getId());
    }

    @Override
    public Set<TaskStatus> allowedTransitions(Task t) {
        return EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED, TaskStatus.APPROVED, TaskStatus.REJECTED, TaskStatus.COMPLETED);
    }

    @Override
    public boolean canSubmit(User u, Task t) {
        // Only assignee can submit
        return t.getAssignee() != null && t.getAssignee().getId().equals(u.getId());
    }

    @Override
    public boolean canApprove(User u, Task t) {
        return check(u, t, PermissionCode.TASK_APPROVE);
    }

    @Override
    public boolean canReject(User u, Task t) {
        return canApprove(u, t);
    }
    
    @Override
    public TaskStatus initialStatus() {
        return TaskStatus.TODO;
    }
    
    @Override
    public boolean canBeReviewed() {
        return true;
    }
    
    @Override
    public boolean canBeSubmitted() {
        return true;
    }
    
    @Override
    public void onComplete(Task t, User u) {
        t.transitionTo(TaskStatus.SUBMITTED, u);
    }
}