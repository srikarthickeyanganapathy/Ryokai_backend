package com.example.taskflow.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TaskDependencyId implements Serializable {

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "depends_on_id")
    private Long dependsOnId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskDependencyId that)) return false;
        return Objects.equals(taskId, that.taskId) && Objects.equals(dependsOnId, that.dependsOnId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, dependsOnId);
    }
}
