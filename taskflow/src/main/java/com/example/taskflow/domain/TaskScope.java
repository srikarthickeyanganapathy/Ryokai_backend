package com.example.taskflow.domain;

import lombok.Getter;

@Getter
public class TaskScope {
    private final boolean isPersonal;
    private final Long orgId;
    private final Long teamId;
    private final Long crewId;

    private TaskScope(boolean isPersonal, Long orgId, Long teamId, Long crewId) {
        this.isPersonal = isPersonal;
        this.orgId = orgId;
        this.teamId = teamId;
        this.crewId = crewId;
    }

    public static TaskScope personal() {
        return new TaskScope(true, null, null, null);
    }

    public static TaskScope org(Long teamId) {
        return new TaskScope(false, null, teamId, null);
    }

    public static TaskScope crew(Long crewId) {
        if (crewId == null) throw new IllegalArgumentException("CrewId cannot be null for crew scope");
        return new TaskScope(false, null, null, crewId);
    }

    public TaskMode getMode() {
        if (isPersonal) return TaskMode.PERSONAL;
        if (crewId != null) return TaskMode.CREW;
        return TaskMode.ORG;
    }
}
