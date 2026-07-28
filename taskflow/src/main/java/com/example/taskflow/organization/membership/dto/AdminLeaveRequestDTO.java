package com.example.taskflow.organization.membership.dto;

public class AdminLeaveRequestDTO {

    private Long successorUserId;
    private boolean dissolve;

    public AdminLeaveRequestDTO() {}

    public AdminLeaveRequestDTO(Long successorUserId, boolean dissolve) {
        this.successorUserId = successorUserId;
        this.dissolve = dissolve;
    }

    public Long getSuccessorUserId() {
        return successorUserId;
    }

    public void setSuccessorUserId(Long successorUserId) {
        this.successorUserId = successorUserId;
    }

    public boolean isDissolve() {
        return dissolve;
    }

    public void setDissolve(boolean dissolve) {
        this.dissolve = dissolve;
    }
}
