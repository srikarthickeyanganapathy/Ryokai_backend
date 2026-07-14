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
public class CrewProjectId implements Serializable {

    @Column(name = "crew_id")
    private Long crewId;

    @Column(name = "project_id")
    private Long projectId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrewProjectId that)) return false;
        return Objects.equals(crewId, that.crewId) && Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(crewId, projectId);
    }
}
