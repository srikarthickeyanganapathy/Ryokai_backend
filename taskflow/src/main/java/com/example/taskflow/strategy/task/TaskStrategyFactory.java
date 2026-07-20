package com.example.taskflow.strategy.task;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.TaskMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TaskStrategyFactory {

    private final Map<TaskMode, TaskLifecycleStrategy> strategies;

    public TaskStrategyFactory(List<TaskLifecycleStrategy> allStrategies) {
        this.strategies = allStrategies.stream()
                .collect(Collectors.toMap(TaskLifecycleStrategy::getSupportedMode, s -> s));
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
