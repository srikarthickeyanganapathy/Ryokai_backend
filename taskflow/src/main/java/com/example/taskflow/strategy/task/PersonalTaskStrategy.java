package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskMode;
import com.example.taskflow.domain.TaskStatus;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.TaskRequestDTO;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
public class PersonalTaskStrategy implements TaskLifecycleStrategy, TaskScopeBehavior {

    private final com.example.taskflow.repository.CrewMemberRepository crewMemberRepository;

    public PersonalTaskStrategy(com.example.taskflow.repository.CrewMemberRepository crewMemberRepository) {
        this.crewMemberRepository = crewMemberRepository;
    }

    @Override
    public TaskMode getSupportedMode() {
        return TaskMode.PERSONAL;
    }

    @Override
    public boolean canEdit(User u, Task t) {
        return t.getCreator() != null && t.getCreator().getId().equals(u.getId());
    }

    @Override
    public boolean canDelete(User u, Task t) {
        return t.getCreator() != null && t.getCreator().getId().equals(u.getId());
    }

    @Override
    public boolean validateDependencyLink(Task source, Task target) {
        return target.getMode() == TaskMode.PERSONAL &&
               target.getCreator() != null &&
               source.getCreator() != null &&
               target.getCreator().getId().equals(source.getCreator().getId());
    }

    @Override
    public Set<TaskStatus> allowedTransitions(Task t) {
        return EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED);
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
    public boolean canCreate(User u, TaskRequestDTO request) {
        return true; // Any authenticated user can create a personal task
    }

    @Override
    public boolean canView(User u, Task t) {
        boolean isCreator = t.getCreator() != null && t.getCreator().getId().equals(u.getId());
        if (isCreator) return true;
        
        if (t.getProject() != null && t.getProject().getSharedCrews() != null) {
            for (com.example.taskflow.domain.Crew crew : t.getProject().getSharedCrews()) {
                if (crewMemberRepository.existsByIdCrewIdAndIdUserId(crew.getId(), u.getId())) {
                    return true;
                }
            }
        }
        return false;
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
    public void onComplete(Task t, User u) {
        t.transitionTo(TaskStatus.COMPLETED, u);
    }
}
