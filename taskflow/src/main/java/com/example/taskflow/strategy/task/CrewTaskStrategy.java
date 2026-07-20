package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskMode;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskRequestDTO;
import com.example.taskflow.repository.CrewMemberRepository;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class CrewTaskStrategy implements TaskLifecycleStrategy, TaskScopeBehavior, Claimable {

    private final CrewMemberRepository crewMemberRepository;

    public CrewTaskStrategy(CrewMemberRepository crewMemberRepository) {
        this.crewMemberRepository = crewMemberRepository;
    }

    @Override
    public TaskMode getSupportedMode() {
        return TaskMode.CREW;
    }

    private boolean isCrewMember(User u, Long crewId) {
        return crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, u.getId());
    }



    @Override
    public boolean canCreate(User u, TaskRequestDTO request) {
        if (request.getCrewId() == null) return false;
        return isCrewMember(u, request.getCrewId());
    }

    @Override
    public boolean canView(User u, Task t) {
        if (t.getCrew() == null) return false;
        return isCrewMember(u, t.getCrew().getId());
    }

    @Override
    public boolean canEdit(User u, Task t) {
        if (t.getCrew() == null) return false;
        return isCrewMember(u, t.getCrew().getId());
    }

    @Override
    public boolean canReassign(User u, Task t) {
        return t.getCreator() != null && t.getCreator().getId().equals(u.getId());
    }

    @Override
    public boolean canArchive(User u, Task t) {
        return t.getCreator() != null && t.getCreator().getId().equals(u.getId());
    }

    @Override
    public boolean canEditDependency(User u, Task t) {
        return t.getCreator() != null && t.getCreator().getId().equals(u.getId());
    }

    @Override
    public boolean canDelete(User u, Task t) {
        return canEdit(u, t);
    }

    @Override
    public boolean validateDependencyLink(Task source, Task target) {
        return target.getMode() == TaskMode.CREW &&
               target.getCrew() != null &&
               source.getCrew() != null &&
               target.getCrew().getId().equals(source.getCrew().getId());
    }

    @Override
    public Set<TaskStatus> allowedTransitions(Task t) {
        return EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED);
    }

    @Override
    public boolean canClaim(User u, Task t) {
        return canEdit(u, t) && (t.getAssignee() == null || t.getAssignee().getId().equals(u.getId()));
    }
    
    @Override
    public TaskStatus initialStatus() {
        return TaskStatus.TODO;
    }
    
    @Override
    public boolean canBeReviewed() {
        return false;
    }
    
    @Override
    public boolean canBeSubmitted() {
        return false;
    }
    
    @Override
    public void onComplete(Task t, User u) {
        t.transitionTo(TaskStatus.COMPLETED, u);
    }
}
