package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskMode;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskRequestDTO;
import com.example.taskflow.security.RoleStrategy;
import com.example.taskflow.security.RoleStrategyFactory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class OrgTaskStrategy implements TaskLifecycleStrategy, TaskScopeBehavior, Approvable {

    private final RoleStrategyFactory roleStrategyFactory;

    public OrgTaskStrategy(RoleStrategyFactory roleStrategyFactory) {
        this.roleStrategyFactory = roleStrategyFactory;
    }

    @Override
    public TaskMode getSupportedMode() {
        return TaskMode.ORG;
    }

    @Override
    public boolean canCreate(User u, TaskRequestDTO request) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canAssign(u);
    }

    @Override
    public boolean canView(User u, Task t) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canViewTask(u, t);
    }

    @Override
    public boolean canReassign(User u, Task t) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canReassign(u, t);
    }

    @Override
    public boolean canArchive(User u, Task t) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canArchive(u, t);
    }

    @Override
    public boolean canEditDependency(User u, Task t) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canEditDependency(u, t);
    }

    @Override
    public boolean canEdit(User u, Task t) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canEdit(u, t); // Relies on existing rank math
    }

    @Override
    public boolean canDelete(User u, Task t) {
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canDelete(u, t);
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
        RoleStrategy strategy = roleStrategyFactory.getStrategy(u);
        return strategy.canReview(u, t); // delegates to existing rank math
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
