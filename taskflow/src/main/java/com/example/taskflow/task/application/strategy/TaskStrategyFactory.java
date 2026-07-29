package com.example.taskflow.task.application.strategy;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.task.domain.model.TaskMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.taskflow.task.domain.strategy.TaskLifecycleStrategy;

@Component
public class TaskStrategyFactory {

    private final Map<TaskMode, TaskLifecycleStrategy> strategies;

    public TaskStrategyFactory(List<TaskLifecycleStrategy> allStrategies) {
        this.strategies = allStrategies.stream()
                .collect(Collectors.toMap(s -> s.getSupportedMode(), s -> s));
    }

    public TaskLifecycleStrategy get(TaskMode mode) {
        if (mode == null) {
            throw new IllegalStateException("Task mode cannot be null");
        }
        TaskLifecycleStrategy strategy = strategies.get(mode);
        if (strategy == null) {
            throw new IllegalStateException("No strategy found for TaskMode: " + mode);
        }
        return strategy;
    }

    public TaskLifecycleStrategy get(Task task) {
        return get(task.getMode());
    }
}