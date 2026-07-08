package com.example.taskflow.dto;

import jakarta.validation.constraints.Size;

public class RejectReasonDTO {

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;

    public RejectReasonDTO() {}

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
