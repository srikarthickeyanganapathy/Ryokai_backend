package com.example.taskflow.domain;

public enum TaskStatus {
    TODO,
    COMPLETED,
    ASSIGNED,
    SUBMITTED,
    APPROVED,
    REJECTED;

    /**
     * Returns true if this status represents a terminal (done) state.
     *
     * Personal/Crew tasks terminate at COMPLETED.
     * Organization tasks terminate at APPROVED.
     *
     * Use this instead of raw {@code != TaskStatus.COMPLETED} checks,
     * which silently mishandle org tasks (whose terminal state is APPROVED,
     * not COMPLETED).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == APPROVED;
    }
}
