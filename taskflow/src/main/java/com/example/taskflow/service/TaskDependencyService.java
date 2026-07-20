package com.example.taskflow.service;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskDependency;
import com.example.taskflow.domain.TaskDependencyId;
import com.example.taskflow.domain.User;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.repository.TaskDependencyRepository;
import com.example.taskflow.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskDependencyService {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskAuditService taskAuditService;
    private final NotificationService notificationService;

    @Transactional
    public void addDependency(Long taskId, Long dependsOnId, User user) {
        if (taskId.equals(dependsOnId)) {
            throw new IllegalArgumentException("A task cannot depend on itself");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        Task dependsOnTask = taskRepository.findById(dependsOnId)
                .orElseThrow(() -> new IllegalArgumentException("Dependent task not found"));

        if (!task.getMode().equals(dependsOnTask.getMode())) {
            throw new IllegalArgumentException("Tasks must be in the same mode to have dependencies");
        }
        
        if (task.isPersonal() && !task.getCreator().getId().equals(dependsOnTask.getCreator().getId())) {
            throw new IllegalArgumentException("Personal tasks can only depend on your own personal tasks");
        }
        if (task.getOrg() != null && !task.getOrg().getId().equals(dependsOnTask.getOrg().getId())) {
            throw new IllegalArgumentException("Tasks must be in the same organization");
        }
        if (task.getCrew() != null && !task.getCrew().getId().equals(dependsOnTask.getCrew().getId())) {
            throw new IllegalArgumentException("Tasks must be in the same crew");
        }

        if (detectCycle(dependsOnTask, task, new HashSet<>())) {
            throw new IllegalStateException("Cannot add dependency: It would create a circular dependency loop.");
        }

        TaskDependency dependency = new TaskDependency();
        dependency.setId(new TaskDependencyId(taskId, dependsOnId));
        dependency.setTask(task);
        dependency.setDependsOn(dependsOnTask);
        dependency.setCreatedBy(user);
        try {
            taskDependencyRepository.saveAndFlush(dependency);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Dependency already exists");
        }
        
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "DEPENDENCY_ADDED", user, "Blocked by task " + dependsOnId, java.util.Map.of("dependsOnId", dependsOnId));
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(user.getId())) {
            notificationService.createAndSend(
                task.getAssignee(),
                user,
                NotificationEvent.TASK_BLOCKED,
                "Task Blocked",
                "Your task has been blocked by another task: " + dependsOnTask.getTitle(),
                task,
                "task-blocked:" + task.getId() + ":" + dependsOnTask.getId(),
                user
            );
        }
    }

    private boolean detectCycle(Task current, Task target, Set<Long> visited) {
        if (current.getId().equals(target.getId())) {
            return true;
        }

        if (visited.contains(current.getId())) {
            return false;
        }

        visited.add(current.getId());

        return taskDependencyRepository.findByTask_Id(current.getId()).stream()
                .map(TaskDependency::getDependsOn)
                .anyMatch(nextTask -> detectCycle(nextTask, target, visited));
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependsOnId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        TaskDependencyId depKey = new TaskDependencyId(taskId, dependsOnId);
        TaskDependency dependency = taskDependencyRepository.findById(depKey)
                .orElseThrow(() -> new IllegalArgumentException("Dependency not found"));
        
        taskDependencyRepository.delete(dependency);
        taskAuditService.recordStatus(task, task.getCurrentStatus().name(), task.getCurrentStatus().name(), "DEPENDENCY_REMOVED", user, "Dependency removed: depends_on=" + dependsOnId, java.util.Map.of("dependsOnId", dependsOnId));
    }
}
